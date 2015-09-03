#!/usr/bin/env bash
#
# Checks the number of lint issues against historical values. Used in
# Travis CI builds to fail when the number increases by exploiting the
# caching mechanism.

if [[ $# != 2 || ! -f $1 ]]; then \
    echo "Usage: $0 <lint.xml file> <historical.xml file>"
    exit 1
fi

lint_file="$1"
historical_file="$2"

xmllint="$(which xmllint)"

if [[ ! -x $xmllint ]]; then \
    echo "Error: cannot find xmllint"
    exit 1
fi

if [[ ! -f $historical_file ]]; then \
  # no cache history, store this one and exit
  cp $lint_file $historical_file
  exit 0
fi

tmp_dir="$(mktemp -d lint.XXXXXXXX)"
trap "rm -rf $tmp_dir" 1 2 3 6 9 14 15

lint_file="$tmp_dir/lint.txt"
hist_file="$tmp_dir/hist.txt"

echo "cat //issue/location" | \
    xmllint --shell $historical_file | \
    grep '<location' >$lint_file
    
echo "cat //issue/location" | \
    xmllint --shell $lint_file | \
    grep '<location' >$hist_file

old_count=$(cat $lint_file | wc -l)
new_count=$(cat $hist_file | wc -l)

echo "Historical count : $old_count, new count : $new_count"

if [[ $new_count > $old_count ]]; then \
    echo "FAILURE: lint issues increased from $old_count to $new_count"
    diff $lint_file $hist_file
    exit 2
fi

if [[ $TRAVIS_PULL_REQUEST == false ]]; then \
    # Okay, we either stayed the same or reduced our number.
    # Write it out so we can check it next build!
    mv $lint_file $historical_file 
fi
