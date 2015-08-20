#!/usr/bin/env bash
#
# Checks the number of lint issues against historical values. Used in
# Travis CI builds to fail when the number increases by exploiting the
# caching mechanism.

# This is to prime the system: when I submitted this change, this is the
# number of lint warnings that existed.
DEFAULT_NUMBER=207

if [[ $# != 3 || ! -f $1 ]]; then \
    echo "Usage: $0 <lint.xml file> <historical file> <success file>"
    exit 1
elif [[ ! -d $(dirname $3) ]]; then \
    echo "Error: directory $(dirname $3) does not exist."
    exit 1
fi

lint_file="$1"
historical_file="$2"
success_file="$3"

xmllint="$(which xmllint)"

if [[ ! -x $xmllint ]]; then \
    echo "Error: cannot find xmllint"
    exit 1
fi

if [[ -f $historical_file ]]; then \
    historical_count="$(cat $historical_file)"
else \
    historical_count=$DEFAULT_NUMBER
fi

new_count="$($xmllint --xpath 'count(//issue)' "$lint_file")"

if [[ $new_count > $historical_count ]]; then \
    echo "FAILURE: lint issues increased from $historical_count to $new_count"
    exit 2
fi

if [[ $TRAVIS_PULL_REQUEST == false ]]; then \
    # Okay, we either stayed the same or reduced our number.
    # Write it out so we can check it next build!
    echo $new_count > $success_file
fi
