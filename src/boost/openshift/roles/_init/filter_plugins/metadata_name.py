#!/usr/bin/python
import json

class FilterModule(object):
    def filters(self):
        return {
            'metadata_name' : self.metadata_name
        }

    def metadata_name(self, stdout):
        dcs = json.loads(stdout)
        items = dcs["items"]
        if not items:
            return ""
        else:
            return items[0]["metadata"]["name"]