# Carriers and carrier dictionaries

The app reads carriers from the `Carriers` table and the translations to and from external systems from
`CarrierMappings` (see "Carriers" in `CLAUDE.md`). The app creates both tables (migration `V024`; the script does it too when they are missing) but does not fill
them: the data a deployment uses is its own and is not kept in this repository.

## File format

```json
{
  "carriers": [
    {"name": "InPost"},
    {"name": "InPost Kurier", "description": "InPost courier, not parcel lockers"}
  ],
  "dictionaries": [
    {
      "dictionary": "global#marketplace#allegro#status",
      "mappings": [
        {"text": "INPOST", "carrier": "InPost"},
        {"text": "INPOST", "carrier": "InPost Kurier"}
      ]
    }
  ]
}
```

- A carrier's `name` is its identifier: it is stored in shipments and never changes.
- `dictionary` is `{scope}#{type}#{name}`: `global#marketplace#{adapter}#import` and
  `global#marketplace#{adapter}#status` for a marketplace, `global#shipping#{adapter}` for a shipping integration
  (the adapter name in lower case, as listed on `/dashboard/carriers`), `{storeId}#supplier#{connection}` for a
  supplier connection.
- The order of `mappings` matters: the first pair with a text decides the carrier that comes in, the first pair
  with a carrier decides the text that goes out.
- Every carrier a pair names has to be in the file or already in the table.

`src/main/resources/local-init/carriers/carriers.example.json` holds public codes only (Allegro carrier ids,
Furgonetka service codes, words of Allegro delivery method names). With the `localdev` profile the app loads it by
itself (migration `V025`, local only).

## Loading

```bash
scripts/carriers/load-carriers.sh --dry-run carriers.json --profile commercelink --region eu-central-1
scripts/carriers/load-carriers.sh carriers.json --profile commercelink --region eu-central-1
```

Everything after the file goes to the `aws` CLI (`--endpoint-url http://localhost:8000` for DynamoDB Local). The
script needs `jq` and the `aws` CLI. It only adds what is missing: a carrier or dictionary that is already there,
for example edited on `/dashboard/carriers`, is left as it is, so running it twice changes nothing. Later changes are
made on the dashboard. The file is checked first (empty values, a pair twice, a carrier nobody defines), and nothing
is loaded when it has problems.

## Rollout

Load the file **before** deploying the version that reads the tables. The script creates both tables when they are
missing, with the schema of `V024` (which then finds them and does nothing), so the app reads complete dictionaries
from its first request. Loaded only after the deploy, the dictionaries are empty in between: nothing breaks, but
carriers are not recognised, so Allegro gets `OTHER` with the carrier name and a Ceneo shipped status is refused (it
needs a carrier code) and retried, ending in the dead-letter queue after three attempts.

`V016` turns on point-in-time recovery and deletion protection for both tables at the next app start.
