cmake_minimum_required(VERSION 3.22.1)
project(adndi CXX)

# NDI SDK fetched by tools/fetch_ndi_sdk.sh (not committed)
set(NDI_SDK ${CMAKE_CURRENT_SOURCE_DIR}/../../../ndi-sdk)
if(NOT EXISTS ${NDI_SDK}/lib/${ANDROID_ABI}/libndi.so)
  message(FATAL_ERROR "NDI SDK missing for ${ANDROID_ABI}: run tools/fetch_ndi_sdk.sh")
endif()

add_library(ndi SHARED IMPORTED)
set_target_properties(ndi PROPERTIES IMPORTED_LOCATION ${NDI_SDK}/lib/${ANDROID_ABI}/libndi.so)

add_library(adndi SHARED adndi.cpp)
target_include_directories(adndi PRIVATE ${NDI_SDK}/include)
target_compile_options(adndi PRIVATE -O2 -Wall)
target_link_libraries(adndi ndi log)
