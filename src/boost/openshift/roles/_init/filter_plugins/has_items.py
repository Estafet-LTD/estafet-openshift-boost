#!/usr/bin/python
import json

class FilterModule(object):
    def filters(self):
        return {
            'has_items' : self.has_items
        }

    def has_items(self, stdout):
        dcs = json.loads(stdout)
        items = dcs["items"]
        if not items:
            return False
        else:
            return True