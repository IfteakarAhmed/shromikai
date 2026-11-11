-- P8 review fixes. V1 to V8 stay unedited.
-- 1. The 8-character prefix is only a display aid; authentication looks the key up by its SHA-256.
--    A UNIQUE prefix makes key creation fail at random when two keys draw the same 8 characters,
--    so uniqueness stays on the hash alone and the prefix gets a plain index for the admin list.
-- 2. The API limits a key name to 60 characters (ApiKeyService); the table said 80. Align the table.
ALTER TABLE api_key DROP CONSTRAINT api_key_prefix_key;
CREATE INDEX api_key_prefix_idx ON api_key (prefix);

UPDATE api_key SET name = left(name, 60) WHERE char_length(name) > 60;
ALTER TABLE api_key DROP CONSTRAINT api_key_name_check;
ALTER TABLE api_key ADD CONSTRAINT api_key_name_check CHECK (char_length(name) BETWEEN 1 AND 60);
