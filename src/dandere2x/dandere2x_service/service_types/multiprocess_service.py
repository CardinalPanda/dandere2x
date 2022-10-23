import copy
import glob
import os
from typing import List

from dandere2x.dandere2x_service.__init__ import Dandere2xServiceThread
from dandere2x.dandere2x_service.service_types.dandere2x_service_interface import Dandere2xServiceInterface
from dandere2x.dandere2x_service_request import Dandere2xServiceRequest
from dandere2x.dandere2xlib.utils.yaml_utils import load_executable_paths_yaml
from dandere2x.dandere2xlib.wrappers.ffmpeg.ffmpeg import divide_and_reencode_video, concat_n_videos, \
    migrate_tracks_contextless, is_file_video


class MultiProcessService(Dandere2xServiceInterface):

    def __init__(self, service_request: Dandere2xServiceRequest):
        """
        Uses multiple Dandere2xServiceThread to upscale a given file. It does this by attempting to split the video
        up into equal parts, then migrating each upscaled-split video into one complete video file.
        """
        super().__init__(service_request=copy.deepcopy(service_request))

        assert is_file_video(ffprobe_dir=load_executable_paths_yaml()['ffprobe'],
                             input_video=self._service_request.input_file),\
            "%s is not a video file!" % self._service_request.input_file

        self._child_threads: List[Dandere2xServiceThread] = []
        self._divided_videos_upscaled: List[str] = []

    def _pre_process(self):

        ffprobe_path = load_executable_paths_yaml()['ffprobe']
        ffmpeg_path = load_executable_paths_yaml()['ffmpeg']

        # Attempt to split the video up into N=3 distinct parts.
        divide_and_reencode_video(ffmpeg_path=ffmpeg_path, ffprobe_path=ffprobe_path,
                                  input_video=self._service_request.input_file,
                                  output_options=self._service_request.output_options,
                                  divide=3, output_dir=self._service_request.workspace)

        # Find all the split video files ffmpeg produced in the folder.
        divided_re_encoded_videos = sorted(glob.glob(os.path.join(self._service_request.workspace, "*.mkv")))

        # Create unique child_requests for each unique video, with the video being the input.
        for x in range(0, len(divided_re_encoded_videos)):
            child_request = copy.deepcopy(self._service_request)
            child_request.input_file = os.path.join(divided_re_encoded_videos[x])
            child_request.output_file = os.path.join(self._service_request.workspace, "non_migrated%d.mkv" % x)
            child_request.workspace = os.path.join(self._service_request.workspace, "subworkspace%d" % x)

            self._divided_videos_upscaled.append(child_request.output_file)
            self._child_threads.append(Dandere2xServiceThread(child_request))

    def run(self):
        self._pre_process()

        for request in self._child_threads:
            request.start()

        for request in self._child_threads:
            request.join()

        self._on_completion()

    def _on_completion(self):
        """
        Converts all self._divided_videos_upscaled into one big video, then migrates the original audio into this
        service request's output file.
        """
        ffmpeg_path = load_executable_paths_yaml()['ffmpeg']
        no_audio = os.path.join(self._service_request.workspace, "noaudio.mkv")
        concat_n_videos(ffmpeg_dir=ffmpeg_path, temp_file_dir=self._service_request.workspace,
                        console_output_dir=self._service_request.workspace, list_of_files=self._divided_videos_upscaled,
                        output_file=no_audio)

        migrate_tracks_contextless(ffmpeg_dir=ffmpeg_path, no_audio=no_audio,
                                   file_dir=self._service_request.input_file,
                                   output_file=self._service_request.output_file,
                                   output_options=self._service_request.output_options,
                                   console_output_dir=self._service_request.workspace)
