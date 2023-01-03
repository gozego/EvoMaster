#!/usr/bin/env bash
fly --target utility set-pipeline -p evo-master -c .ci/pipeline.yml
