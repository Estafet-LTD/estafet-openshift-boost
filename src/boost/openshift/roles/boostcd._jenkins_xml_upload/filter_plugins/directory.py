#!/usr/bin/python
import os

class FilterModule(object):
    def filters(self):
        return {
            'directory' : self.directory,

        }

    def directory(self, path):
        head, tail = os.path.split(path)
        return head
