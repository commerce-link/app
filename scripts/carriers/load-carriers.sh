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
# The tables are created by the app on startup (migration V024), so run it after the app has started once.
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

PROBLEMS=$(jq -r '
    (.carriers[] | select(.name == "") | "a carrier without a name"),
    (.dictionaries[] | select(.dictionary == "") | "a dictionary without a key"),
    (.dictionaries[] | .dictionary as $d | .mappings[]
        | select(.text == "" or .carrier == "") | "\($d): a pair without a text or a carrier"),
    (.dictionaries[] | .dictionary as $d | .mappings
        | group_by([(.text | ascii_upcase), (.carrier | ascii_upcase)])[] | select(length > 1)
        | "\($d): the pair \(.[0].text)=\(.[0].carrier) is there twice")
    ' <<<"$NORMALIZED")

EXISTING_CARRIERS=$(aws_cli dynamodb scan --table-name "$CARRIERS_TABLE" \
    --projection-expression "#n" --expression-attribute-names '{"#n":"name"}' --output json \
    | jq -c '[.Items[].name.S]')

# A pair has to name a carrier of the file or of the table; dictionaries look carriers up regardless of case.
UNKNOWN=$(jq -r --argjson existing "$EXISTING_CARRIERS" '
    ([.carriers[].name] + $existing | map(ascii_downcase)) as $known
    | .dictionaries[] | .dictionary as $d | .mappings[]
    | select((.carrier | ascii_downcase) as $c | $known | index($c) | not)
    | "\($d): unknown carrier \(.carrier)"' <<<"$NORMALIZED")

if [[ -n "$PROBLEMS$UNKNOWN" ]]; then
    printf '%s\n' "$PROBLEMS" "$UNKNOWN" | sed '/^$/d' >&2
    echo "Nothing loaded." >&2
    exit 1
fi

created=0
kept=0

# put-item only when the key is free; prints "created" or "kept", fails on anything else
put_if_absent() {
    local table="$1" key="$2" item="$3" output
    if [[ "$DRY_RUN" == true ]]; then
        if aws_cli dynamodb get-item --table-name "$table" --key "$(jq -c --arg k "$key" "{$4: {S: \$k}}" <<<'{}')" \
            --output json | jq -e '.Item' >/dev/null 2>&1; then
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
