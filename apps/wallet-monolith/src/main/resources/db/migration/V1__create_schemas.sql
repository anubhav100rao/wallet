-- V1__create_schemas.sql
-- Creates one Postgres schema per bounded context.
-- Phase 1: all schemas in one database (wallet_dev).
-- Phase 2: each schema promotes to its own logical database.

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS wallet;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS transaction;
CREATE SCHEMA IF NOT EXISTS shared;
