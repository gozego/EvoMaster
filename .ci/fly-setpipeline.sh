#!/usr/bin/env bash
fly --target sdet set-pipeline -p evo-master -c .ci/pipeline.yml
