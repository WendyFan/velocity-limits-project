# Velocity Limits

A Spring Boot application that processes fund load attempts and enforces velocity limits per customer.

## Rules

- Max **3 loads** per day
- Max **$5,000** per day
- Max **$20,000** per week (Monday–Sunday UTC)
- Duplicate load IDs for the same customer are silently ignored

## Requirements

- Java 17+
- Maven 3.8+

## Build

```bash
mvn package -DskipTests
```

## Run

```bash
java -jar target/velocity-limits-0.0.1-SNAPSHOT.jar \
  --app.input.file=data/input.txt \
  --app.output.file=data/my-output.txt
```

- `app.input.file` — path to the input file (one JSON object per line)
- `app.output.file` — path to write results (optional; results always print to stdout)

## Input Format

Each line is a JSON object:

```json
{"id":"1234","customer_id":"1234","load_amount":"$123.45","time":"2018-01-01T00:00:00Z"}
```

## Output Format

Each accepted or rejected load produces one line:

```json
{"id":"1234","customer_id":"1234","accepted":true}
```

## Compare Output Against Expected

After running, diff your output against the expected output:

```bash
diff <(tr -d '\r' < data/my-output.txt) <(tr -d '\r' < data/expected-output.txt)
```

No output means the files match exactly.

## Run Tests

```bash
mvn test
```
