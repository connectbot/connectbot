#!/usr/bin/env bash

if [[ $# != 2 ]]; then \
    echo "Usage: $0 [ndk_version] [ndk_dest_dir]"
    exit 1
fi

ndk_version="$1"
ndk_dest_dir="$2"

if ! hash 7za 2> /dev/null; then \
    echo "p7zip is not installed!"
    exit 1
fi

if [[ ! -x ${ndk_dest_dir}/ndk-build ]]; then \
    tmpfile="$(mktemp)"
    trap 'rm -f "${tmpfile}"' EXIT
    curl https://dl.google.com/android/ndk/android-ndk-${ndk_version}-linux-x86_64.bin -o "${tmpfile}"
    7za x -y -o"$HOME" "${tmpfile}" | grep -v 'ing  '
else
    echo NDK already downloaded at ${ndk_dest_dir}
fi
