#!/usr/bin/env zsh

EXECUTOR=${EXECUTOR_NUMBER:-0}
export SERVER_PORT=$((1500 + ${EXECUTOR}))
export SERVER_HOST="0.0.0.0"
export CNC_SERVER_PORT=$((1550 + ${EXECUTOR}))
export CNC_SERVER_HOST="0.0.0.0"

# CnC expects server backend at BACKEND_HOST:BACKEND_PORT
export BACKEND_PORT=${SERVER_PORT}
export BACKEND_HOST=${SERVER_HOST}

RED='\033[0;31m'
GREEN='\033[0;32m'
RESET='\033[0m'

START_SERVER=1
START_CNC_SERVER=1

if nc -z ${SERVER_HOST} ${SERVER_PORT} > /dev/null 2>&1; then
  echo "${RED}WARNING: Server - An unknown server listens on port ${SERVER_PORT}. Not starting the Server.${RESET}"
  START_SERVER=0
fi

if nc -z ${CNC_SERVER_HOST} ${CNC_SERVER_PORT} > /dev/null 2>&1; then
  echo "${RED}WARNING: CnC Server - An unknown server listens on port ${CNC_SERVER_PORT}. Not starting the CnC Server.${RESET}"
  START_CNC_SERVER=0
fi

rm server.log
rm cnc_server.log

if [ $START_SERVER -eq 1 ]; then
  echo "Building and starting Server on port ${SERVER_PORT}"
  make -C Server build
  nohup make -C Server run > server.log 2>&1 &
  SERVER_PID=$!
  echo $SERVER_PID > server.pid
  echo "${GREEN}Started Server on port ${SERVER_PORT} with PID ${SERVER_PID}.${RESET}"
else
  echo "${RED}WARNING: Using unknown Server on port ${SERVER_PORT}${RESET}"
fi

if [ $START_CNC_SERVER -eq 1 ]; then
  echo "Building and starting CnC Server on port ${CNC_SERVER_PORT}"
  make -C CnC build
  nohup make -C CnC run > cnc_server.log 2>&1 &
  CNC_SERVER_PID=$!
  echo $CNC_SERVER_PID > cnc_server.pid
  echo "${GREEN}Started CnC Server on port ${CNC_SERVER_PORT} with PID ${CNC_SERVER_PID}.${RESET}"
else
  echo "${RED}WARNING: Using unknown CnC Server on port ${CNC_SERVER_PORT}${RESET}"
fi

echo $SERVER_PORT > server.port
echo $CNC_SERVER_PORT > cnc_server.port

echo "Waiting for the server(s) to be serving…"

while ! nc -z ${SERVER_HOST} ${SERVER_PORT}; do
  sleep 1
  if [ $START_SERVER -eq 1 ] && ! kill -0 $SERVER_PID > /dev/null 2>&1; then
    echo "${RED}ERROR: Server was terminated before it was up and running.${RESET}"
    echo "Contents of server.log:"
    cat server.log
    exit 1
  fi
done

while ! nc -z ${CNC_SERVER_HOST} ${CNC_SERVER_PORT}; do
  sleep 1
  if [ $START_CNC_SERVER -eq 1 ] && ! kill -0 $CNC_SERVER_PID > /dev/null 2>&1; then
    echo "${RED}ERROR: CnC Server was terminated before it was up and running.${RESET}"
    echo "Contents of cnc_server.log:"
    cat cnc_server.log
    exit 1
  fi
done

echo "${GREEN}Servers are reachable.${RESET}"
