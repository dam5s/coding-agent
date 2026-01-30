# Coding Agent

## Running it

1. Either export OPENAI_API_KEY or set it in `local.properties` (see `local.properties.example`)
2. Create a `prompt.txt` with your prompt.
3. Run the app

```
./gradlew run --args="prompt.txt my-project"
```

## Example prompts

First prompt
```
Generate a python app in app.py, it is a CLI for managing TODO items that should persist to a json file next to app.py.
```

Second prompt
```
There is an app.py with a program for managing TODOs update it to add support for updating a TODO item.
```
