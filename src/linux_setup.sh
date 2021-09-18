#!/bin/sh

# Requirements...
if ! command -v cmake &> /dev/null
then
    echo "cmake could not be found"
    exit
fi

if ! command -v ffmpeg > /dev/null 2>&1; then
    echo could not find path to ffmpeg binary
    exit
fi

# Create d2x-cpp binary
cd ../dandere2x_cpp
cmake CMakeLists.txt
make

FILE=dandere2x_cpp
if ! test -f "$FILE"; then
    echo "$FILE does not exist.. not sure what's wrong..."
    exit 1
fi

#  get into the src folder from dandere2x_cpp
cd ../src

# create the externals folder and move over the recently made d2x_cpp binary
mkdir -p externals
mv ../dandere2x_cpp/dandere2x_cpp ./externals/dandere2x_cpp

# download waifu2x-ncnn-vulkan and extract it the way d2x expects it
cd externals
wget https://github.com/nihui/waifu2x-ncnn-vulkan/releases/download/20200606/waifu2x-ncnn-vulkan-20200606-linux.zip
unzip waifu2x-ncnn-vulkan-20200606-linux.zip
mv waifu2x-ncnn-vulkan-20200606-linux ./waifu2x-ncnn-vulkan
rm waifu2x-ncnn-vulkan-20200606-linux.zip
cd ..

# create workspace folder (this needs to exist I think?)
mkdir -p workspace
echo success, be sure to install requirements and you should be good to go with "python3 main.py" now. 
