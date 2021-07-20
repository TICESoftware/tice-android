#!/usr/bin/env zsh

if [ -f server.port ]
then
  rm server.port
fi

if [ -f cnc_server.port ]
then
  rm cnc_server.port
fi

if [ -f server.pid ]
then
  kill -TERM $(cat server.pid) || true
  rm server.pid
  echo "Stopped Server"
else
  echo "Server not running"
fi

if [ -f cnc_server.pid ]
then
  kill -TERM $(cat cnc_server.pid) || true
  rm cnc_server.pid
  echo "Stopped CnC Server"
else
  echo "CnC Server not running"
fi
