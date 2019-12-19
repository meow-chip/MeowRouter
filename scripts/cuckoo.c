#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define ROW_NUM (1024) // ROW_NUM must be the power of 2, and the maximum value is 65536
#define ROW_NUM_MASK (ROW_NUM - 1)
#define SLOT_NUM (4)

#define KEY_TYPE uint32_t
#define VALUE_TYPE uint8_t

/* cuckoo hash table

Usage:
 - struct Table t;
    create a hash table instance
 
 - insert(&t, key, value);
    insert (key, value) into the table t.
    It returns 1 if the insertation is successful.
    If the key already exists, it still returns 1 but updates the value.

 - lookup(&t, key, &result); 
    lookup the key from the table t.
    It returns 1 if the key is found, and sets the result to the correspond value.

 - remove_key(&t, key);
    delete key from the table t.
    It returns 1 if the delete is successful, namely, the key was in the table.
*/

#define boolean uint8_t

struct Row {
    KEY_TYPE keys[SLOT_NUM];
    VALUE_TYPE values[SLOT_NUM];
    boolean valid[SLOT_NUM]; // boolean value
};

struct Table {
    struct Row rows[ROW_NUM];
};


// a fake hash implementation
// TODO: replace it with a real implementation
uint32_t hash(KEY_TYPE k) {
    return k;
}

// lookup the key k at row r.
// it returns the index of the key if it exists
int lookup_at_row(struct Row *r, KEY_TYPE k, VALUE_TYPE *result) {
    for (int i = 0; i < SLOT_NUM; i++) {
        if (r->valid[i] && r->keys[i] == k) {
            *result = r->values[i];
            return i;
        }
    }
    return -1;
}

boolean lookup(struct Table *t, KEY_TYPE k, VALUE_TYPE *result) {
    int h = hash(k);
    int r1_id = h & ROW_NUM_MASK;
    int r2_id = (h >> 16) & ROW_NUM_MASK;
    
    if (lookup_at_row(&t->rows[r1_id], k, result) != -1) {
        return 1;
    }

    if (lookup_at_row(&t->rows[r2_id], k, result) != -1) {
        return 1;
    }

    return 0;
}


// empty_slot returns empty slot index if there is an empty slot,
// otherwise returns -1.
int empty_slot(struct Row *r) {
    for (int i = 0; i < SLOT_NUM; i++) {
        if (r->valid[i] == 0) {
            return i;
        }
    }
    return -1;
}

// insert the (k, v) pair into the row r.
boolean insert_into_row(struct Row *r, KEY_TYPE k, VALUE_TYPE v) {
    int slot_id = empty_slot(r);
    if (slot_id >= 0) {
        r->keys[slot_id] = k;
        r->values[slot_id] = v;
        r->valid[slot_id] = 1;
        return 1;
    }
    return 0;
}

// insert the (k, v) pair into the table r.
boolean insert(struct Table *t, KEY_TYPE k, VALUE_TYPE v) {
    int h = hash(k);
    int r1_id = h & ROW_NUM_MASK;
    int r2_id = (h >> 16) & ROW_NUM_MASK;
    fflush(stdout);

    // check if the key is alread in the table. If it is, modify the value.
    VALUE_TYPE tmp_result;
    int tmp_index;
    tmp_index = lookup_at_row(&t->rows[r1_id], k, &tmp_result);
    if (tmp_index != -1) {
        t->rows[r1_id].values[tmp_index] = v;
        return 1;
    }
    tmp_index = lookup_at_row(&t->rows[r2_id], k, &tmp_result);
    if (tmp_index != -1) {
        t->rows[r2_id].values[tmp_index] = v;
        return 1;
    }

    // try to insert into the first(r1_id) row
    if (insert_into_row(&t->rows[r1_id], k, v)) {
        return 1;
    }

    // try to insert into the second(r2_id) row
    if (insert_into_row(&t->rows[r2_id], k, v)) {
        return 1;
    }

    // TODO: shift the tuples in the row r1_id and r2_id to other rows, to reserve an empty slot

    return 0;
}

boolean remove_key_from_row(struct Row *r, KEY_TYPE k) {
    for (int i = 0; i < SLOT_NUM; i++) {
        if (r->valid[i] && r->keys[i] == k) {
            r->valid[i] = 0;
            return 1;
        }
    }
    return 0;
}

boolean remove_key(struct Table *t, KEY_TYPE k) {
    int h = hash(k);
    int r1_id = h & ROW_NUM_MASK;
    int r2_id = (h >> 16) & ROW_NUM_MASK;

    if (remove_key_from_row(&t->rows[r1_id], k)) {
        return 1;
    }

    if (remove_key_from_row(&t->rows[r2_id], k)) {
        return 1;
    }

    return 0;
}

void test_smoke() {
    struct Table t;
    memset(&t, 0, sizeof(t));

    // insert (42, 1) and (43, 2)
    insert(&t, 42, 1);
    insert(&t, 43, 2);
    
    VALUE_TYPE v;
    // lookup the key 42
    assert(lookup(&t, 42, &v));
    assert(v == 1);
    // lookup the key 43
    assert(lookup(&t, 43, &v));
    assert(v == 2);

    // remove the key 42
    assert(remove_key(&t, 42));
    assert(lookup(&t, 42, &v) == 0);

    // keep modifying the key 42
    for (int i = 3; i <= 50; i++) {
        assert(insert(&t, 42, i));
        assert(lookup(&t, 42, &v));
        assert(v == i);
    }

    printf("smoke: Passed\n");
}

void test_utilization() {
    float factor = 0.4;
    int keys[SLOT_NUM*ROW_NUM];

    struct Table t;
    memset(&t, 0, sizeof(t));
    for (int i = 0; i < SLOT_NUM * ROW_NUM * factor; i++) {
        keys[i] = (rand() << 16) ^ rand();
        assert(insert(&t, keys[i], i));
    }

    // verify all keys are in the table
    for (int i = 0; i < SLOT_NUM * ROW_NUM * factor; i++) {
        VALUE_TYPE v;
        assert(lookup(&t, keys[i], &v));
        assert(v == (VALUE_TYPE)i);
    }
    
    printf("utilization: Passed\n");
}

void test() {
    test_smoke();
    test_utilization();
}

int main(int argc, char *argv[]) {
    test();
    return 0;
}