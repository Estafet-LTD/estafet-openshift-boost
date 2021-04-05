#!/usr/bin/python
import json

class FilterModule(object):
    def filters(self):
        return {
            'has_item' : self.has_item
        }

    def has_item(self, stdout, deployment):
        dcs = json.loads(stdout)
        items = dcs["items"]
        for item in items:
            if items["metadata"]["name"] == deployment:
                return True
        return False