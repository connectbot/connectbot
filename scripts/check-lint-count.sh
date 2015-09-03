#!/usr/bin/env bash
#
# Checks the number of lint issues against historical values. Used in
# Travis CI builds to fail when the number increases by exploiting the
# caching mechanism.

# This is to prime the system: when I submitted this change, this is the
# number of lint warnings that existed.
DEFAULT_NUMBER=207

if [[ $# != 2 || ! -f $1 ]]; then \
    echo "Usage: $0 <lint.xml file> <historical.xml file>"
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

if [[ ! -f $2 ]]; then \
  # no cache history, store this one and exit
  cp $1 $2
  exit 0
fi

echo "cat //issue/location" | xmllint --shell $historical_file | grep '<location' >/tmp/hist.$$
echo "cat //issue/location" | xmllint --shell $lint_file | grep '<location' >/tmp/lint.$$

old_count=$(cat /tmp/hist.$$ | wc -l)
new_count=$(cat /tmp/lint.$$ | wc -l)

echo "Historical count : $old_count, new count : $new_count"

if [[ $new_count > $old_count ]]; then \
    echo "FAILURE: lint issues increased from $old_count to $new_count"
    diff /tmp/lint.$$ /tmp/hist.$$
    rm -f /tmp/lint.$$ /tmp/hist.$$
    exit 2
fi

rm -f /tmp/lint.$$ /tmp/hist.$$

if [[ $TRAVIS_PULL_REQUEST == false ]]; then \
    # Okay, we either stayed the same or reduced our number.
    # Write it out so we can check it next build!
    mv $lint_file $historical_file 
fi
