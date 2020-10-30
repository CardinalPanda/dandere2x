import ctypes
import logging
import os
import sys
import threading
import time

# todo
# This could probably be improved visually for the user.. it's not the most pleasing to look at
# Also, in a very niche case the GUI didn't catch up with the deletion of files, so it ceased updating
from dandere2x.dandere2x_service import Dandere2xServiceContext, Dandere2xController
from dandere2xlib.utils.dandere2x_utils import get_operating_system


class Status(threading.Thread):

    def __init__(self, context: Dandere2xServiceContext, controller: Dandere2xController):
        self.con = context
        self.controller = controller
        self.log = logging.getLogger()

        # Threading Specific
        threading.Thread.__init__(self, name="StatusThread")

    def join(self, timeout=None):
        self.log.info("Join called.")
        threading.Thread.join(self, timeout)
        self.log.info("Join finished.")

    def run(self):
        self.log.info("Run called.")
        last_10 = [0]

        path, name = os.path.split(self.con.service_request.input_file)  # get file name only

        for x in range(1, self.con.frame_count - 1):

            if not self.controller.is_alive():
                break

            percent = int(((x + 1) / (self.con.frame_count - 1)) * 100)

            average = 0
            for time_count in last_10:
                average = average + time_count

            average = round(average / len(last_10), 2)

            # sys.stdout.write('\r')
            # sys.stdout.write("[File: %s][Frame: [%s] %i%%]    Average of Last 10 Frames: %s sec / frame" % (name,x, percent, average))

            if get_operating_system() == 'win32':
                ctypes.windll.kernel32.SetConsoleTitleW(
                    "[File: %s][Frame: [%s] %i%%]    Average of Last 10 Frames: %s sec / frame" % (
                        name, x, percent, average))
            else:
                sys.stdout.write('\r')
                sys.stdout.write("[File: %s][Frame: [%s] %i%%]    Average of Last 10 Frames: %s sec / frame" % (
                    name, x, percent, average))

            if len(last_10) == 10:
                last_10.pop(0)

            now = time.time()

            while x >= self.controller.get_current_frame() and self.controller.is_alive():
                time.sleep(.00001)

            later = time.time()
            difference = float(later - now)
            last_10.append(difference)