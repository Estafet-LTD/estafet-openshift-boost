#!/usr/bin/python
import os

class FilterModule(object):
    def filters(self):
        return {
            'filename' : self.filename,
            
        }

    def filename(self, path):
        head, tail = os.path.split(path)
        return tail