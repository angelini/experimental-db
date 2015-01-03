#!/bin/bash

set -x

DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
NUM=$(expr $(find "${DIR}" -name "node-*" | wc -l) - 1)

find "${DIR}" -name "exdb" | xargs chmod a+x

case "${1}" in
  start)
    for ((i=0; i<="${NUM}"; i++)); do
      "${DIR}/node-0${i}/exdb" start
    done
    ;;

  stop)
    for ((i=0; i<="${NUM}"; i++)); do
      "${DIR}/node-0${i}/exdb" stop
    done
    ;;

  run)
    for ((i=0; i<="${NUM}"; i++)); do
      "${DIR}/node-0${i}/exdb" run
    done
    ;;

  *)
    echo "usage ${0} start"
    echo "      ${0} stop"
    ;;
esac
