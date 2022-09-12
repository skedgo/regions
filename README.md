# Specs Validator

We provide a validator that checks for JSON files in the directories:

- `regions`
- `pt-static-feeds`
- `pt-realtime-feeds`

This validator not only checks that JSON files follows the specs defined in `schemas`, but also checks for semantics.

In order to run this validator:

- navigate to `tools/validator` directory 
- run the following command: `./mvnw test`

If all JSON files are correct and follow required semantics, then expected output after running this command is:

```
[INFO] Results:
[INFO]
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```
