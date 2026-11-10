cmake_minimum_required(VERSION 3.18)
project(radonusb C)
add_library(radonusb SHARED usbaudio.c)
target_link_libraries(radonusb log)
