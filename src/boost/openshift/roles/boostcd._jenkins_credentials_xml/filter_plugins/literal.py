#!/usr/bin/python

class FilterModule(object):
    def filters(self):
        return {
            'literal' : self.literal
        }

    def literal(self, str):
        return str