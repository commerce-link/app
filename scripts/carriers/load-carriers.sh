#!/usr/bin/env bash
# Loads carriers and carrier dictionaries from a JSON file into the Carriers and CarrierMappings tables.
# Only adds what is missing: an existing carrier or dictionary (e.g. edited on /dashboard/carriers) is never changed.
# Every argument after the file goes to the aws CLI, so the same script serves production and DynamoDB Local.
#
#   scripts/carriers/load-carriers.sh [--dry-run] FILE [aws options...]
#
#   scripts/carriers/load-carriers.sh carriers.json --profile commercelink --region eu-central-1
#   scripts/carriers/load-carriers.sh --dry-run carriers.json --profile commercelink --region eu-central-1
#   scripts/carriers/load-carriers.sh src/main/resources/local-init/carriers/carriers.example.json \
#       --endpoint-url http://localhost:8000 --region eu-central-1
#
# Creates the two tables when they are missing, with the schema of migration V024 (which then finds them), so the
# data can be loaded before the deploy that starts reading it.
set -euo pipefail

CARRIERS_TABLE="Carriers"
DICTIONARIES_TABLE="CarrierMappings"

usage() {
    sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
    shift
fi
[[ $# -ge 1 ]] || usage
FILE="$1"
shift
AWS_ARGS=("$@")

for tool in jq aws; do
    command -v "$tool" >/dev/null || { echo "Missing $tool" >&2; exit 1; }
done
[[ -f "$FILE" ]] || { echo "No such file: $FILE" >&2; exit 1; }

# bash 3.2 (macOS) treats an empty array as unset under set -u
aws_cli() {
    aws "$@" ${AWS_ARGS[@]+"${AWS_ARGS[@]}"}
}

# The same rules as the dashboard: no empty name, text or carrier, spaces collapsed, no pair twice in a dictionary.
NORMALIZED=$(jq -c '
    def clean: tostring | gsub("\\s+"; " ") | sub("^ "; "") | sub(" $"; "");
    {
      carriers: [(.carriers // [])[] | {name: (.name | clean), description: ((.description // "") | clean)}],
      dictionaries: [(.dictionaries // [])[] | {
        dictionary: (.dictionary | clean),
        mappings: [.mappings[] | {text: (.text | clean), carrier: (.carrier | clean)}]
      }]
    }' "$FILE")

# The keys the app reads (CarrierDictionary): a key of another shape loads fine and is never read, so its carriers
# would silently go unrecognized. The adapter name is in lower case, as the app builds it.
KEY_PATTERN='^(global#marketplace#[^#A-Z]+#(import|status)|global#shipping#[^#A-Z]+|[^#]+#supplier#[^#]+)$'

PROBLEMS=$(jq -r --arg pattern "$KEY_PATTERN" '
    (.carriers[] | select(.name == "") | "a carrier without a name"),
    (.dictionaries[] | select(.dictionary == "") | "a dictionary without a key"),
    (.dictionaries[] | .dictionary | select(. != "" and (test($pattern) | not))
        | "\(.): not a dictionary key, expected global#marketplace#<adapter>#import|status, global#shipping#<adapter> or <storeId>#supplier#<connection>"),
    (.dictionaries[] | .dictionary as $d | .mappings[]
        | select(.text == "" or .carrier == "") | "\($d): a pair without a text or a carrier"),
    (.dictionaries[] | .dictionary as $d | .mappings
        | group_by([(.text | ascii_upcase), (.carrier | ascii_upcase)])[] | select(length > 1)
        | "\($d): the pair \(.[0].text)=\(.[0].carrier) is there twice")
    ' <<<"$NORMALIZED")

# the file alone is enough to refuse it, before any call to AWS
if [[ -n "$PROBLEMS" ]]; then
    printf '%s\n' "$PROBLEMS" >&2
    echo "Nothing loaded." >&2
    exit 1
fi

# only a missing table is "no": an expired session or a wrong region stops the script instead of creating anything
table_exists() {
    local output
    if output=$(aws_cli dynamodb describe-table --table-name "$1" 2>&1); then
        return 0
    elif grep -q "ResourceNotFoundException" <<<"$output"; then
        return 1
    fi
    echo "$output" >&2
    exit 1
}

EXISTING_CARRIERS='[]'
if table_exists "$CARRIERS_TABLE"; then
    EXISTING_CARRIERS=$(aws_cli dynamodb scan --table-name "$CARRIERS_TABLE" \
        --projection-expression "#n" --expression-attribute-names '{"#n":"name"}' --output json \
        | jq -c '[.Items[].name.S]')
fi

# A pair has to name a carrier of the file or of the table; dictionaries look carriers up regardless of case.
UNKNOWN=$(jq -r --argjson existing "$EXISTING_CARRIERS" '
    ([.carriers[].name] + $existing | map(ascii_downcase)) as $known
    | .dictionaries[] | .dictionary as $d | .mappings[]
    | select((.carrier | ascii_downcase) as $c | $known | index($c) | not)
    | "\($d): unknown carrier \(.carrier)"' <<<"$NORMALIZED")

if [[ -n "$UNKNOWN" ]]; then
    printf '%s\n' "$UNKNOWN" >&2
    echo "Nothing loaded." >&2
    exit 1
fi

# the schema of V024_CreateCarrierTables: a string hash key, on-demand capacity
ensure_table() {
    local table="$1" key="$2"
    if table_exists "$table"; then
        return
    fi
    if [[ "$DRY_RUN" == true ]]; then
        echo "would create table $table"
        return
    fi
    aws_cli dynamodb create-table --table-name "$table" \
        --attribute-definitions "AttributeName=$key,AttributeType=S" \
        --key-schema "AttributeName=$key,KeyType=HASH" \
        --billing-mode PAY_PER_REQUEST >/dev/null
    aws_cli dynamodb wait table-exists --table-name "$table"
    echo "created   table $table"
}

ensure_table "$CARRIERS_TABLE" name
ensure_table "$DICTIONARIES_TABLE" dictionary

created=0
kept=0

# put-item only when the key is free; prints "created" or "kept", fails on anything else
put_if_absent() {
    local table="$1" key="$2" item="$3" output
    if [[ "$DRY_RUN" == true ]]; then
        if aws_cli dynamodb get-item --table-name "$table" --key "$(jq -c --arg k "$key" "{$4: {S: \$k}}" <<<'{}')" \
            --output json 2>/dev/null | jq -e '.Item' >/dev/null 2>&1; then
            echo "kept"
        else
            echo "created"
        fi
        return
    fi
    if output=$(aws_cli dynamodb put-item --table-name "$table" --item "$item" \
        --condition-expression "attribute_not_exists(#k)" --expression-attribute-names "{\"#k\":\"$4\"}" 2>&1); then
        echo "created"
    elif grep -q "ConditionalCheckFailedException" <<<"$output"; then
        echo "kept"
    else
        echo "$output" >&2
        return 1
    fi
}

while IFS= read -r carrier; do
    name=$(jq -r '.name' <<<"$carrier")
    same=$(jq -r --arg n "$name" '.[] | select(ascii_downcase == ($n | ascii_downcase))' <<<"$EXISTING_CARRIERS" | head -1)
    if [[ -n "$same" && "$same" != "$name" ]]; then
        echo "kept      carrier $same (the file says $name)"
        kept=$((kept + 1))
        continue
    fi
    item=$(jq -c '{name: {S: .name}} + (if .description == "" then {} else {description: {S: .description}} end)' <<<"$carrier")
    result=$(put_if_absent "$CARRIERS_TABLE" "$name" "$item" name)
    printf '%-9s carrier %s\n' "$result" "$name"
    if [[ "$result" == created ]]; then created=$((created + 1)); else kept=$((kept + 1)); fi
done < <(jq -c '.carriers[]' <<<"$NORMALIZED")

while IFS= read -r dictionary; do
    key=$(jq -r '.dictionary' <<<"$dictionary")
    item=$(jq -c '{
        dictionary: {S: .dictionary},
        mappings: {L: [.mappings[] | {M: {text: {S: .text}, carrier: {S: .carrier}}}]},
        version: {N: "1"}
    }' <<<"$dictionary")
    result=$(put_if_absent "$DICTIONARIES_TABLE" "$key" "$item" dictionary)
    printf '%-9s dictionary %s (%s pairs)\n' "$result" "$key" "$(jq '.mappings | length' <<<"$dictionary")"
    if [[ "$result" == created ]]; then created=$((created + 1)); else kept=$((kept + 1)); fi
done < <(jq -c '.dictionaries[]' <<<"$NORMALIZED")

if [[ "$DRY_RUN" == true ]]; then
    echo "Dry run: $created would be created, $kept already there."
else
    echo "Done: $created created, $kept already there."
fi
