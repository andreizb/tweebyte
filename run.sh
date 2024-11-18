#!/bin/bash

# Define the supported application stacks
STACK_ASYNC="async"
STACK_REACTIVE="reactive"

# Check if the application stack is specified and supported
if [[ -z "$2" ]]; then
    echo "Application stack not specified."
    exit 1
elif [ "$2" != "$STACK_ASYNC" ] && [ "$2" != "$STACK_REACTIVE" ]; then
    echo "Application stack not supported."
    exit 1
fi

# Handle the start and stop commands
case "$1" in
    start)
        # Start the containers for the specified application stack
        STACK=$2 docker compose up -d --force-recreate
        ;;
    stop)
        # Stop and remove the containers and volumes for the specified application stack
        STACK=$2 docker compose down -v --rmi local
        ;;
    *)
        # Unsupported command
        echo "Unsupported command."
        exit 1
        ;;
esac
