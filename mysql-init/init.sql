CREATE DATABASE IF NOT EXISTS vetsm_usuarios CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vetsm_catalogo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vetsm_ventas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vetsm_agenda CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON vetsm_usuarios.* TO 'vetsm'@'%';
GRANT ALL PRIVILEGES ON vetsm_catalogo.* TO 'vetsm'@'%';
GRANT ALL PRIVILEGES ON vetsm_ventas.* TO 'vetsm'@'%';
GRANT ALL PRIVILEGES ON vetsm_agenda.* TO 'vetsm'@'%';
FLUSH PRIVILEGES;