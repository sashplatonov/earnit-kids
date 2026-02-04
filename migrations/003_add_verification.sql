ALTER TABLE families 
ADD COLUMN is_verified BOOLEAN DEFAULT TRUE,
ADD COLUMN verification_token VARCHAR(255);

-- For new registrations, we will set is_verified to FALSE explicitly in the code.
-- Existing users default to TRUE so they are not locked out.
