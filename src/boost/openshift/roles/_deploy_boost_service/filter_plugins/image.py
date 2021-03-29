#!/usr/bin/python
import json

class FilterModule(object):
    def filters(self):
        return {
            'image' : self.image
        }

    def image(self, json):
        print json
        dcs = json.loads(json)
        items = dcs["items"]
        if not items:
            return ""
        else:
            return items[0]["spec"]["template"]["spec"]["containers"][0]["image"]