#!/bin/bash

diff <(head -n $1 tokenized_result.txt) <(head -n $1 token_test_tokenized_ok.txt)
