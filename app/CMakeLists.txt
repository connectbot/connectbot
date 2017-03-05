cmake_minimum_required (VERSION 3.4.1)

add_library (com_google_ase_Exec SHARED "src/main/cpp/com_google_ase_Exec.cpp")
find_library (log-lib log)
target_link_libraries (com_google_ase_Exec ${log-lib})
