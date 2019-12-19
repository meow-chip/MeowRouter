#! /usr/bin/env python3

import random
import crcmod

crc32_func1 = crcmod.mkCrcFun(0x104c11db7, initCrc=0, xorOut=0xFFFFFFFF)
crc32_func2 = crcmod.mkCrcFun(0x1cf2420b5, initCrc=0, xorOut=0xFFFFFFFF)

class Cuckoo(object):
    def __init__(self, buckets_num=1024, slots_num=4, shift_times=3):
        self.buckets_num = buckets_num
        self.slots_num = slots_num
        self.shift_times = shift_times
        self.buckets = [[None] * slots_num for _ in range(buckets_num)]

    def bucket_id(self, key):
        #return key % self.buckets_num, key // self.buckets_num % self.buckets_num
        return crc32_func1(str(key).encode()) % self.buckets_num, crc32_func2(str(key).encode()) % self.buckets_num

    def put_into_bucket(self, key, bucket):
        assert(len(bucket) == self.slots_num)
        for i in range(self.slots_num):
            if bucket[i] == None:
                bucket[i] = key
                return True
        return False

    def shift(self, bucket, depth = 3):
        if depth == 0:
            return False

        for i in range(self.slots_num):
            k = bucket[i]
            assert(k != None)
            id1, id2 = self.bucket_id(k)
            # swap to make sure k is currently in self.bucket[id1]
            if k in self.buckets[id2]:
                id1, id2 = id2, id1
            other_b = self.buckets[id2]

            if None not in other_b and id1 != id2:
                self.shift(other_b, depth-1)

            for j in range(self.slots_num):
                if other_b[j] == None:
                    other_b[j] = k
                    bucket[i] = None
                    return True
        return False

    def put(self, key):
        id1, id2 = self.bucket_id(key)
        b1 = self.buckets[id1]
        b2 = self.buckets[id2]

        if self.put_into_bucket(key, b1):
            return True
        if self.put_into_bucket(key, b2):
            return True
        if self.shift(b1, depth=self.shift_times):
            assert(self.put_into_bucket(key, b1))
            return True
        if self.shift(b2, depth=self.shift_times):
            assert(self.put_into_bucket(key, b2))
            return True

        return False
    
    def find(self, key):
        id1, id2 = self.bucket_id(key)
        b1 = self.buckets[id1]
        b2 = self.buckets[id2]
        return key in b1 or key in b2

def sim1():
    buckets_num = 1024
    slots_num = 4
    shift_times = 2
    factor = 0.8
    for round in range(300):
        c = Cuckoo(buckets_num=buckets_num, slots_num=slots_num, shift_times=shift_times)
        keys = []
        for i in range(int(buckets_num*slots_num*factor)):
            k = random.randint(0, 2000000)
            keys.append(k)
            if not c.put(k):
                print(f"Failed on inserting {i}-th key")
                assert(False)

        for k in keys:
            assert(c.find(k))
            
    print("sim1: Pass")

if __name__ == "__main__":
    sim1()