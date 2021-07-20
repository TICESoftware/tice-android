#!/usr/bin/env zsh

if [ ! -f server.pid ] && [ ! -f cnc_server.pid ]
then
  echo "Server not running. Aborting."
  exit 1;
fi

SERVER_PORT=$(cat server.port)
CNC_SERVER_PORT=$(cat cnc_server.port)

export SERVER_ADDRESS="http://10.0.2.2:${SERVER_PORT}"
export WS_SERVER_ADDRESS="ws://10.0.2.2:${SERVER_PORT}"
export CNC_SERVER_ADDRESS="http://10.0.2.2:${CNC_SERVER_PORT}"

echo "Running tests with…"
echo "\tServer at ${SERVER_ADDRESS}"
echo "\tWebsocket at ${WS_SERVER_ADDRESS}"
echo "\tCnC at ${CNC_SERVER_ADDRESS}"

bundle exec fastlane tests

echo "This was the server log:"
cat server.log
echo ""

echo "This was the cnc server log:"
cat cnc_server.log
echo ""

exit 0;
