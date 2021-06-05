#!/usr/bin/python
from pathlib import Path


class FilterModule(object):
    def filters(self):
        return {
            'stage_file': self.stage_file
        }

    def stage_file(self, file, stage, directory):
        stage_file = Path("{}/{}-{}".format(directory, stage, file))
        default_file = Path("{}/{}-{}".format(directory, "default", file))
        if stage_file.is_file():
            return str(stage_file)
        elif default_file.is_file():
            return str(default_file)
        else:
            return ""
