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

xqilla="$(which xqilla)"

if [[ ! -x $xqilla ]]; then \
  echo "Error: cannot find xqilla"
  exit 1
fi

if [[ ! -f $historical_file ]]; then \
  # no cache history, store this one and exit
  cp "$lint_file" "$historical_file"
  exit 0
fi

tmp_dir="$(mktemp -d lint.XXXXXXXX)"
trap 'rm -rf $tmp_dir' ERR EXIT

lint_results="$tmp_dir/lint.txt"
hist_results="$tmp_dir/hist.txt"

run_query() {
  local xqilla_script='string-join(//issue/location/(concat("file=", @file, " line=", @line, " column=", @column, " message=", ../@message)), "&#10;")'
  xqilla -i "$1" <(echo "$xqilla_script") | sed "s,$PWD/,,g" > "$2"
}

run_query "$lint_file" "$lint_results"
run_query "$historical_file" "$hist_results"

old_count=$(wc -l < "$hist_results")
new_count=$(wc -l < "$lint_results")

echo "Historical count: $old_count, new count: $new_count"

if [[ $new_count > $old_count ]]; then \
  echo "FAILURE: lint issues increased from $old_count to $new_count"
  diff -u "$hist_results" "$lint_results"
  if [[ $TRAVIS_PULL_REQUEST != false ]]; then \
    exit 2
  fi
fi

if [[ $TRAVIS_PULL_REQUEST == false ]]; then \
  # Okay, we either stayed the same or reduced our number.
  # Write it out so we can check it next build!
  cp "$lint_file" "$historical_file"
fi
