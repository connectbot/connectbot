cmake_minimum_required (VERSION 3.4.1)

project(ConnectBot)
message(STATUS "CMake arguments: ${CMAKE_ARGS}")

add_library(com_google_ase_Exec SHARED "src/main/cpp/com_google_ase_Exec.cpp")

find_library(log-lib log)
target_link_libraries(com_google_ase_Exec ${log-lib})

include(ProcessorCount)
ProcessorCount(Ncpu)

set(CFLAGS_EXTERNAL_LIB "--target=${CMAKE_C_COMPILER_TARGET} -w -std=gnu17 -fno-strict-overflow -D_FORTIFY_SOURCE=2 -fstack-protector-all -Wstack-protector --param ssp-buffer-size=1 -fPIE -fno-default-inline -pipe -fPIC")
set(CXXFLAGS_EXTERNAL_LIB "--target=${CMAKE_CXX_COMPILER_TARGET} -w -std=gnu++17 -fno-strict-overflow -D_FORTIFY_SOURCE=2 -fstack-protector-all -Wstack-protector --param ssp-buffer-size=1 -fPIE -fno-default-inline -pipe -fPIC")
set(ASM_FLAGS_EXTERNAL_LIB "--target=${CMAKE_CXX_COMPILER_TARGET} -w -D_FORTIFY_SOURCE=2 -fPIE -pipe -fPIC")

include(ExternalProject)
ExternalProject_Add( zlib
  GIT_REPOSITORY https://github.com/madler/zlib.git
  GIT_TAG v1.3.1
  PREFIX zlib
  CMAKE_ARGS -DCMAKE_MODULE_PATH=${CMAKE_MODULE_PATH} -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
    -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE} -DCMAKE_INSTALL_PREFIX=${CMAKE_BINARY_DIR}/zlib
    -DCMAKE_C_FLAGS=${CFLAGS_EXTERNAL_LIB} -DCMAKE_CXX_FLAGS=${CXXFLAGS_EXTERNAL_LIB}
    -DANDROID_ABI=${ANDROID_ABI} -DANDROID_PLATFORM=${ANDROID_PLATFORM}
    -DCMAKE_C_COMPILER=${CMAKE_C_COMPILER} -DCMAKE_CXX_COMPILER=${CMAKE_CXX_COMPILER}
    -DCMAKE_C_COMPILER_TARGET=${CMAKE_C_COMPILER_TARGET} -DCMAKE_CXX_COMPILER_TARGET=${CMAKE_CXX_COMPILER_TARGET}
  BUILD_BYPRODUCTS <INSTALL_DIR>/lib/libz.a
)

set(PROTOBUF_VER "29.1")
set(PROTOBUF_HOST "${CMAKE_HOST_SYSTEM_NAME}-${CMAKE_HOST_SYSTEM_PROCESSOR}")
set(PROTOBUF_PROTOC "v${PROTOBUF_VER}/protoc-${PROTOBUF_VER}-${PROTOBUF_HOST}.zip")
ExternalProject_Add( protoc
  URL https://github.com/protocolbuffers/protobuf/releases/download/${PROTOBUF_PROTOC}
  PREFIX ${CMAKE_BINARY_DIR}/protoc
  SOURCE_DIR ${CMAKE_BINARY_DIR}/protoc/dist
  STAMP_DIR ${CMAKE_BINARY_DIR}/protoc/stamp
  DOWNLOAD_NO_PROGRESS false
  UPDATE_COMMAND true
  CONFIGURE_COMMAND true
  BUILD_COMMAND true
  INSTALL_COMMAND true
)
ExternalProject_Add( protobuf
  GIT_REPOSITORY https://github.com/protocolbuffers/protobuf.git
  GIT_TAG v${PROTOBUF_VER}
  GIT_SHALLOW 1
  GIT_SUBMODULES_RECURSE 1
  GIT_SUBMODULES_SHALLOW 1
  PREFIX ${CMAKE_BINARY_DIR}/protobuf
  SOURCE_DIR ${CMAKE_BINARY_DIR}/protobuf/src
  STAMP_DIR ${CMAKE_BINARY_DIR}/protobuf/stamp
  BINARY_DIR ${CMAKE_BINARY_DIR}/protobuf/build
  INSTALL_DIR ${CMAKE_BINARY_DIR}/protobuf/install
  DEPENDS zlib protoc
  CMAKE_ARGS -DCMAKE_MODULE_PATH=${CMAKE_MODULE_PATH} -DCMAKE_INSTALL_PREFIX=<INSTALL_DIR>
    -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE} -DCMAKE_BUILD_TYPE=Release
    -DWITH_PROTOC=${CMAKE_BINARY_DIR}/protoc/dist/bin/protoc -Dprotobuf_BUILD_TESTS=OFF
    -DANDROID_ABI=${ANDROID_ABI} -DANDROID_PLATFORM=${ANDROID_PLATFORM}
    -DCMAKE_C_FLAGS=${CFLAGS_EXTERNAL_LIB} -DCMAKE_CXX_FLAGS=${CXXFLAGS_EXTERNAL_LIB}
    -DCMAKE_C_COMPILER=${CMAKE_C_COMPILER} -DCMAKE_C_COMPILER_TARGET=${CMAKE_C_COMPILER_TARGET}
    -DCMAKE_CXX_COMPILER=${CMAKE_CXX_COMPILER} -DCMAKE_CXX_COMPILER_TARGET=${CMAKE_CXX_COMPILER_TARGET}
  BUILD_BYPRODUCTS 
    <INSTALL_DIR>/lib/libabsl_bad_any_cast_impl.a
    <INSTALL_DIR>/lib/libabsl_bad_optional_access.a
    <INSTALL_DIR>/lib/libabsl_bad_variant_access.a
    <INSTALL_DIR>/lib/libabsl_base.a
    <INSTALL_DIR>/lib/libabsl_city.a
    <INSTALL_DIR>/lib/libabsl_civil_time.a
    <INSTALL_DIR>/lib/libabsl_cord.a
    <INSTALL_DIR>/lib/libabsl_cord_internal.a
    <INSTALL_DIR>/lib/libabsl_cordz_functions.a
    <INSTALL_DIR>/lib/libabsl_cordz_handle.a
    <INSTALL_DIR>/lib/libabsl_cordz_info.a
    <INSTALL_DIR>/lib/libabsl_cordz_sample_token.a
    <INSTALL_DIR>/lib/libabsl_crc32c.a
    <INSTALL_DIR>/lib/libabsl_crc_cord_state.a
    <INSTALL_DIR>/lib/libabsl_crc_cpu_detect.a
    <INSTALL_DIR>/lib/libabsl_crc_internal.a
    <INSTALL_DIR>/lib/libabsl_debugging_internal.a
    <INSTALL_DIR>/lib/libabsl_demangle_internal.a
    <INSTALL_DIR>/lib/libabsl_die_if_null.a
    <INSTALL_DIR>/lib/libabsl_examine_stack.a
    <INSTALL_DIR>/lib/libabsl_exponential_biased.a
    <INSTALL_DIR>/lib/libabsl_failure_signal_handler.a
    <INSTALL_DIR>/lib/libabsl_flags_commandlineflag.a
    <INSTALL_DIR>/lib/libabsl_flags_commandlineflag_internal.a
    <INSTALL_DIR>/lib/libabsl_flags_config.a
    <INSTALL_DIR>/lib/libabsl_flags_internal.a
    <INSTALL_DIR>/lib/libabsl_flags_marshalling.a
    <INSTALL_DIR>/lib/libabsl_flags_parse.a
    <INSTALL_DIR>/lib/libabsl_flags_private_handle_accessor.a
    <INSTALL_DIR>/lib/libabsl_flags_program_name.a
    <INSTALL_DIR>/lib/libabsl_flags_reflection.a
    <INSTALL_DIR>/lib/libabsl_flags_usage.a
    <INSTALL_DIR>/lib/libabsl_flags_usage_internal.a
    <INSTALL_DIR>/lib/libabsl_graphcycles_internal.a
    <INSTALL_DIR>/lib/libabsl_hash.a
    <INSTALL_DIR>/lib/libabsl_hashtablez_sampler.a
    <INSTALL_DIR>/lib/libabsl_int128.a
    <INSTALL_DIR>/lib/libabsl_kernel_timeout_internal.a
    <INSTALL_DIR>/lib/libabsl_leak_check.a
    <INSTALL_DIR>/lib/libabsl_log_entry.a
    <INSTALL_DIR>/lib/libabsl_log_flags.a
    <INSTALL_DIR>/lib/libabsl_log_globals.a
    <INSTALL_DIR>/lib/libabsl_log_initialize.a
    <INSTALL_DIR>/lib/libabsl_log_internal_check_op.a
    <INSTALL_DIR>/lib/libabsl_log_internal_conditions.a
    <INSTALL_DIR>/lib/libabsl_log_internal_fnmatch.a
    <INSTALL_DIR>/lib/libabsl_log_internal_format.a
    <INSTALL_DIR>/lib/libabsl_log_internal_globals.a
    <INSTALL_DIR>/lib/libabsl_log_internal_log_sink_set.a
    <INSTALL_DIR>/lib/libabsl_log_internal_message.a
    <INSTALL_DIR>/lib/libabsl_log_internal_nullguard.a
    <INSTALL_DIR>/lib/libabsl_log_internal_proto.a
    <INSTALL_DIR>/lib/libabsl_log_severity.a
    <INSTALL_DIR>/lib/libabsl_log_sink.a
    <INSTALL_DIR>/lib/libabsl_low_level_hash.a
    <INSTALL_DIR>/lib/libabsl_malloc_internal.a
    <INSTALL_DIR>/lib/libabsl_periodic_sampler.a
    <INSTALL_DIR>/lib/libabsl_random_distributions.a
    <INSTALL_DIR>/lib/libabsl_random_internal_distribution_test_util.a
    <INSTALL_DIR>/lib/libabsl_random_internal_platform.a
    <INSTALL_DIR>/lib/libabsl_random_internal_pool_urbg.a
    <INSTALL_DIR>/lib/libabsl_random_internal_randen.a
    <INSTALL_DIR>/lib/libabsl_random_internal_randen_hwaes.a
    <INSTALL_DIR>/lib/libabsl_random_internal_randen_hwaes_impl.a
    <INSTALL_DIR>/lib/libabsl_random_internal_randen_slow.a
    <INSTALL_DIR>/lib/libabsl_random_internal_seed_material.a
    <INSTALL_DIR>/lib/libabsl_random_seed_gen_exception.a
    <INSTALL_DIR>/lib/libabsl_random_seed_sequences.a
    <INSTALL_DIR>/lib/libabsl_raw_hash_set.a
    <INSTALL_DIR>/lib/libabsl_raw_logging_internal.a
    <INSTALL_DIR>/lib/libabsl_scoped_set_env.a
    <INSTALL_DIR>/lib/libabsl_spinlock_wait.a
    <INSTALL_DIR>/lib/libabsl_stacktrace.a
    <INSTALL_DIR>/lib/libabsl_status.a
    <INSTALL_DIR>/lib/libabsl_statusor.a
    <INSTALL_DIR>/lib/libabsl_strerror.a
    <INSTALL_DIR>/lib/libabsl_str_format_internal.a
    <INSTALL_DIR>/lib/libabsl_strings.a
    <INSTALL_DIR>/lib/libabsl_strings_internal.a
    <INSTALL_DIR>/lib/libabsl_string_view.a
    <INSTALL_DIR>/lib/libabsl_symbolize.a
    <INSTALL_DIR>/lib/libabsl_synchronization.a
    <INSTALL_DIR>/lib/libabsl_throw_delegate.a
    <INSTALL_DIR>/lib/libabsl_time.a
    <INSTALL_DIR>/lib/libabsl_time_zone.a
    <INSTALL_DIR>/lib/libabsl_vlog_config_internal.a
    <INSTALL_DIR>/lib/libprotobuf.a
    <INSTALL_DIR>/lib/libupb.a
    <INSTALL_DIR>/lib/libutf8_range.a
    <INSTALL_DIR>/lib/libutf8_validity.a
)

ExternalProject_Add( ncurses
  GIT_REPOSITORY https://github.com/mirror/ncurses.git
  GIT_SHALLOW	ON
  GIT_TAG v6.4
  PREFIX ${CMAKE_BINARY_DIR}/ncurses
  SOURCE_DIR ${CMAKE_BINARY_DIR}/ncurses/src
  STAMP_DIR ${CMAKE_BINARY_DIR}/ncurses/stamp
  BINARY_DIR ${CMAKE_BINARY_DIR}/ncurses/build
  INSTALL_DIR ${CMAKE_BINARY_DIR}/ncurses/install
  UPDATE_COMMAND true
  PATCH_COMMAND sed -i /tsearch/d <SOURCE_DIR>/configure
  CONFIGURE_COMMAND <SOURCE_DIR>/configure --enable-pc-files prefix=<INSTALL_DIR>
    --without-shared --without-debug --without-manpages --disable-stripping --with-termlib
    CC=${CMAKE_C_COMPILER} CFLAGS=${CFLAGS_EXTERNAL_LIB}\ -DHAVE_TSEARCH=0
    CXX=${CMAKE_CXX_COMPILER} CXXFLAGS=${CXXFLAGS_EXTERNAL_LIB}
    LD=${CMAKE_C_COMPILER} AR=${CMAKE_AR} RANLIB=${CMAKE_RANLIB}
    --host=${CMAKE_C_COMPILER_TARGET} --target=${CMAKE_CXX_COMPILER_TARGET}
  BUILD_COMMAND make -C <BINARY_DIR> -j${Ncpu}
  BUILD_BYPRODUCTS <INSTALL_DIR>/lib/libncurses.so <INSTALL_DIR>/include/ncurses/ncurses.h
    <INSTALL_DIR>/lib/libncurses.a <INSTALL_DIR>/lib/libtinfo.a
  INSTALL_COMMAND make -C <BINARY_DIR> install
)

ExternalProject_Add( gmp
  GIT_REPOSITORY https://github.com/alisw/GMP.git
  GIT_PROGRESS true
  GIT_SHALLOW	ON
  GIT_TAG v6.2.1
  PREFIX ${CMAKE_BINARY_DIR}/gmp
  INSTALL_DIR ${CMAKE_BINARY_DIR}/gmp/install
  UPDATE_DISCONNECTED true
  PATCH_COMMAND sed -i /SUBDIRS\ =/s/\ doc// Makefile.in
  CONFIGURE_COMMAND ./configure --enable-cxx -enable-alloca=alloca --prefix=<INSTALL_DIR>
    --disable-shared --with-pic
    CC=${CMAKE_C_COMPILER} CFLAGS=${CFLAGS_EXTERNAL_LIB}
    CXX=${CMAKE_CXX_COMPILER} CXXFLAGS=${CXXFLAGS_EXTERNAL_LIB}
    LD=${CMAKE_C_COMPILER} AR=${CMAKE_AR} RANLIB=${CMAKE_RANLIB}
    --host=${CMAKE_C_COMPILER_TARGET} --target=${CMAKE_CXX_COMPILER_TARGET}
  BUILD_COMMAND make -j${Ncpu}
  BUILD_IN_SOURCE true
  INSTALL_COMMAND make install
)

ExternalProject_Add( nettle
  GIT_REPOSITORY https://gitlab.com/gnutls/nettle.git
  GIT_TAG nettle_3.10_release_20240616
  GIT_SHALLOW 1
  GIT_CONFIG core.autocrlf=input
  PREFIX nettle
  INSTALL_DIR ${CMAKE_BINARY_DIR}/nettle/install
  CONFIGURE_COMMAND autoreconf -fvi
  COMMAND ./configure --prefix=<INSTALL_DIR>
    CC=${CMAKE_C_COMPILER} CFLAGS=${CFLAGS_EXTERNAL_LIB}
    CXX=${CMAKE_CXX_COMPILER} CXXFLAGS=${CXXFLAGS_EXTERNAL_LIB}
    LD=${CMAKE_C_COMPILER} AR=${CMAKE_AR} RANLIB=${CMAKE_RANLIB} ASM_FLAGS=${ASM_FLAGS_EXTERNAL_LIB}
    --host=${CMAKE_C_COMPILER_TARGET} --target=${CMAKE_CXX_COMPILER_TARGET}
    --with-include-path=${CMAKE_BINARY_DIR}/gmp/install/include
    --with-lib-path=${CMAKE_BINARY_DIR}/gmp/install/lib
  BUILD_COMMAND make -j${Ncpu} libnettle.a
  BUILD_IN_SOURCE 1
  BUILD_BYPRODUCTS <INSTALL_DIR>/lib/libnettle.a
  INSTALL_COMMAND make install PREFIX=<INSTALL_DIR>
)
add_dependencies(nettle gmp)

set(MOSH_PC "$ENV{PKG_CONFIG_PATH}")
set(MOSH_PC "${CMAKE_BINARY_DIR}/zlib/share/pkgconfig:${MOSH_PC}")
set(MOSH_PC "${CMAKE_BINARY_DIR}/protobuf/install/lib/pkgconfig:${MOSH_PC}")
set(MOSH_PC "${CMAKE_BINARY_DIR}/ncurses/install/lib/pkgconfig:${MOSH_PC}")
set(MOSH_PC "${CMAKE_BINARY_DIR}/nettle/install/lib/pkgconfig:${MOSH_PC}")
set(MOSH_PKG_CONFIG_PATH "${MOSH_PC}")
set(MOSH_PATH "${CMAKE_BINARY_DIR}/protoc/dist/bin:$ENV{PATH}")
ExternalProject_Add( mosh
  PREFIX ${CMAKE_BINARY_DIR}/mosh
  SOURCE_DIR ${CMAKE_SOURCE_DIR}/../libs/mosh
  BINARY_DIR ${CMAKE_BINARY_DIR}/mosh/build
  INSTALL_DIR ${CMAKE_BINARY_DIR}/mosh/install
  DOWNLOAD_COMMAND rsync -a --exclude=.git <SOURCE_DIR>/ <BINARY_DIR>/
  PATCH_COMMAND env --chdir <BINARY_DIR> patch -p1 -i ${CMAKE_SOURCE_DIR}/../libs/mosh.patch
  COMMAND sed -i /AC_PROG_RANLIB/d <BINARY_DIR>/configure.ac
  COMMAND sed -i /AM_PROG_AR/a\ AC_PROG_LIBTOOL <BINARY_DIR>/configure.ac
  COMMAND sed -i /AM_PROG_AR/a\ LT_INIT <BINARY_DIR>/configure.ac
  COMMAND env --chdir <BINARY_DIR> libtoolize --force --recursive
  CONFIGURE_COMMAND autoreconf -fvi
  COMMAND env PATH=${MOSH_PATH} PKG_CONFIG_PATH=${MOSH_PKG_CONFIG_PATH}
    ./configure --enable-shared --enable-static --enable-pic --enable-static-libraries
      --with-crypto-library=nettle --without-utempter --with-ncurses=yes
      CC=${CMAKE_C_COMPILER} CFLAGS=${CFLAGS_EXTERNAL_LIB}\ -DHAVE_LANGINFO_H
      CXX=${CMAKE_CXX_COMPILER} CXXFLAGS=${CXXFLAGS_EXTERNAL_LIB}\ -DHAVE_LANGINFO_H
      LD=${CMAKE_C_COMPILER} AR=${CMAKE_AR} RANLIB=${CMAKE_RANLIB}
      --host=${CMAKE_C_COMPILER_TARGET} --target=${CMAKE_CXX_COMPILER_TARGET}
  BUILD_COMMAND make -j${Ncpu} all
  BUILD_IN_SOURCE FALSE
  BUILD_ALWAYS TRUE
  BUILD_BYPRODUCTS <INSTALL_DIR>/lib/libmoshclient.a <INSTALL_DIR>/lib/libmoshserver.a
    <INSTALL_DIR>/lib/libmoshcrypto.a <INSTALL_DIR>/lib/libmoshnetwork.a
    <INSTALL_DIR>/lib/libmoshprotos.a <INSTALL_DIR>/lib/libmoshstatesync.a
    <INSTALL_DIR>/lib/libmoshterminal.a <INSTALL_DIR>/lib/libmoshutil.a
  INSTALL_COMMAND cp -t <INSTALL_DIR>/lib/
    src/crypto/libmoshcrypto.a
    src/network/libmoshnetwork.a
    src/protobufs/libmoshprotos.a
    src/statesync/libmoshstatesync.a
    src/terminal/libmoshterminal.a
    src/util/libmoshutil.a
  COMMAND llvm-objcopy --redefine-sym main=mosh_client_main src/frontend/mosh-client.o src/frontend/libmosh-client.o
  COMMAND llvm-objcopy --redefine-sym main=mosh_server_main src/frontend/mosh-server.o src/frontend/libmosh-server.o
  COMMAND ${CMAKE_AR} rcs <INSTALL_DIR>/lib/libmoshclient.a
    src/frontend/terminaloverlay.o
    src/frontend/stmclient.o
    src/frontend/libmosh-client.o
  COMMAND ${CMAKE_AR} rcs <INSTALL_DIR>/lib/libmoshserver.a
    src/frontend/libmosh-server.o
  LOG_DOWNLOAD ON
  LOG_PATCH ON
  LOG_CONFIGURE ON
  LOG_BUILD ON
  LOG_OUTPUT_ON_FAILURE TRUE
)
add_dependencies(mosh zlib protobuf protoc ncurses nettle)

add_library(moshclient SHARED "src/main/cpp/org_mosh_MoshClient.cpp")
add_dependencies(moshclient mosh)

target_link_libraries(moshclient PRIVATE log
  "${CMAKE_BINARY_DIR}/mosh/install/lib/libmoshclient.a"
  "${CMAKE_BINARY_DIR}/mosh/install/lib/libmoshcrypto.a"
  "${CMAKE_BINARY_DIR}/mosh/install/lib/libmoshnetwork.a"
  "${CMAKE_BINARY_DIR}/mosh/install/lib/libmoshprotos.a"
  "${CMAKE_BINARY_DIR}/mosh/install/lib/libmoshstatesync.a"
  "${CMAKE_BINARY_DIR}/mosh/install/lib/libmoshterminal.a"
  "${CMAKE_BINARY_DIR}/mosh/install/lib/libmoshutil.a"
  "${CMAKE_BINARY_DIR}/zlib/lib/libz.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_bad_any_cast_impl.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_bad_optional_access.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_bad_variant_access.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_base.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_city.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_civil_time.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_cord.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_cord_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_cordz_functions.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_cordz_handle.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_cordz_info.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_cordz_sample_token.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_crc32c.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_crc_cord_state.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_crc_cpu_detect.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_crc_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_debugging_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_demangle_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_die_if_null.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_examine_stack.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_exponential_biased.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_failure_signal_handler.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_commandlineflag.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_commandlineflag_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_config.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_marshalling.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_parse.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_private_handle_accessor.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_program_name.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_reflection.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_usage.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_flags_usage_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_graphcycles_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_hash.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_hashtablez_sampler.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_int128.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_kernel_timeout_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_leak_check.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_entry.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_flags.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_globals.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_initialize.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_check_op.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_conditions.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_fnmatch.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_format.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_globals.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_log_sink_set.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_message.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_nullguard.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_internal_proto.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_severity.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_log_sink.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_low_level_hash.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_malloc_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_periodic_sampler.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_distributions.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_distribution_test_util.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_platform.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_pool_urbg.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_randen.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_randen_hwaes.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_randen_hwaes_impl.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_randen_slow.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_internal_seed_material.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_seed_gen_exception.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_random_seed_sequences.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_raw_hash_set.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_raw_logging_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_scoped_set_env.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_spinlock_wait.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_stacktrace.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_status.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_statusor.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_strerror.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_str_format_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_strings.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_strings_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_string_view.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_symbolize.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_synchronization.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_throw_delegate.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_time.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_time_zone.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libabsl_vlog_config_internal.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libprotobuf.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libupb.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libutf8_range.a"
  "${CMAKE_BINARY_DIR}/protobuf/install/lib/libutf8_validity.a"
  "${CMAKE_BINARY_DIR}/ncurses/install/lib/libtinfo.a"
  "${CMAKE_BINARY_DIR}/nettle/install/lib/libnettle.a"
)
target_compile_options(moshclient PRIVATE -fvisibility=hidden)
target_link_options(moshclient PRIVATE -pie -Wl,-z,relro -Wl,-z,now -Wl,--gc-sections)

set(terminfo_zip "${CMAKE_ANDROID_ASSETS_DIRECTORIES}/terminfo.zip")
get_filename_component(BASE_DIR "${terminfo_zip_dir}" DIRECTORY)
file(MAKE_DIRECTORY "${BASE_DIR}")
add_custom_command(
  TARGET ncurses POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E chdir ${CMAKE_BINARY_DIR}/ncurses/install
    zip -X -r ${terminfo_zip} share/terminfo
  COMMENT "Stashing terminfo into ${terminfo_zip}"
)
