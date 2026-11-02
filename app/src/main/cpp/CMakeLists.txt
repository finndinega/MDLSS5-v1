cmake_minimum_required(VERSION 3.22.1)
project(mdlss5)
find_program(GLSLANG_VALIDATOR glslangValidator REQUIRED)
find_package(Python3 COMPONENTS Interpreter REQUIRED)
set(SHADER ${CMAKE_CURRENT_SOURCE_DIR}/shaders/upscale.comp)
set(SPV ${CMAKE_CURRENT_BINARY_DIR}/upscale.comp.spv)
set(SHADER_HEADER ${CMAKE_CURRENT_BINARY_DIR}/mdlss5_shader.h)
add_custom_command(OUTPUT ${SHADER_HEADER}
  COMMAND ${GLSLANG_VALIDATOR} -V ${SHADER} -o ${SPV}
  COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/embed_spirv.py ${SPV} ${SHADER_HEADER}
  DEPENDS ${SHADER} ${CMAKE_CURRENT_SOURCE_DIR}/embed_spirv.py
  VERBATIM)
add_custom_target(mdlss5_shader DEPENDS ${SHADER_HEADER})
add_library(mdlss5 SHARED native-lib.cpp ${SHADER_HEADER})
add_dependencies(mdlss5 mdlss5_shader)
target_include_directories(mdlss5 PRIVATE ${CMAKE_CURRENT_BINARY_DIR})
target_link_libraries(mdlss5 log vulkan)
target_compile_features(mdlss5 PRIVATE cxx_std_17)
