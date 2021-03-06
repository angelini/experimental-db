#!/bin/bash

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

REDIS_LOG="${DIR}/redis.log"
REDIS_PID="${DIR}/redis.pid"

SERF_LOG="${DIR}/serf.log"
SERF_PID="${DIR}/serf.pid"

JAR_PATH="${DIR}/exdb.jar"

. "${DIR}/env.sh"

host () {
  echo "${1}" | cut -d ":" -f 1
}

port () {
  echo "${1}" | cut -d ":" -f 2
}

case "${1}" in
  start)
    if [[ ! -f "${REDIS_PID}" ]]; then
      redis-server --port $(port "${REDIS_ADDR}") \
                   > "${REDIS_LOG}" &
      echo "${!}" > "${REDIS_PID}"
    fi

    if [[ ! -f "${SERF_PID}" ]]; then
      serf agent \
           -node="${API_ADDR}" \
           -bind="${SERF_BIND}" \
           -rpc-addr="${SERF_RPC}" \
           -join="${SERF_SEED}" \
           -profile=local \
           > "${SERF_LOG}" &
      echo "${!}" > "${SERF_PID}"
    fi
    ;;

  stop)
    if [[ -f "${REDIS_PID}" ]]; then
      kill $(cat "${REDIS_PID}")
      rm "${REDIS_PID}"
    fi

    if [[ -f "${SERF_PID}" ]]; then
      kill $(cat "${SERF_PID}")
      rm "${SERF_PID}"
    fi
    ;;

  run)
    java -jar "${JAR_PATH}"
    ;;

  *)
    echo "usage ${0} start"
    echo "      ${0} stop"
    ;;
esac
