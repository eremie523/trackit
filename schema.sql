-- Create Database
CREATE DATABASE IF NOT EXISTS `trackit`;
USE `trackit`;

-- Create Table: users
CREATE TABLE IF NOT EXISTS `users` (
  `id` CHAR(36) NOT NULL,
  `email` VARCHAR(255) NOT NULL UNIQUE,
  `passwordHash` VARCHAR(255) NOT NULL,
  `fullName` VARCHAR(255) NOT NULL,
  `role` VARCHAR(20) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `nationalID` VARCHAR(50) NOT NULL,
  `gender` VARCHAR(20) NOT NULL,
  `phoneNumber` VARCHAR(20) NOT NULL,
  `createdAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create Table: patients
CREATE TABLE IF NOT EXISTS `patients` (
  `id` CHAR(36) NOT NULL,
  `userId` CHAR(36) NOT NULL UNIQUE,
  `isMarried` BOOLEAN NOT NULL,
  `bloodType` VARCHAR(10),
  `genotype` VARCHAR(10),
  `dob` TIMESTAMP NOT NULL,
  `age` INT NOT NULL,
  `addressCountry` VARCHAR(100),
  `addressCity` VARCHAR(100),
  `addressState` VARCHAR(100),
  `addressStreetAddress` VARCHAR(255),
  `addressZipCode` VARCHAR(20),
  `createdAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_patients_users` FOREIGN KEY (`userId`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create Table: doctors
CREATE TABLE IF NOT EXISTS `doctors` (
  `userId` CHAR(36) NOT NULL,
  `createdAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`userId`),
  CONSTRAINT `fk_doctors_users` FOREIGN KEY (`userId`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create Table: tokens
CREATE TABLE IF NOT EXISTS `tokens` (
  `id` CHAR(36) NOT NULL,
  `tokenHash` VARCHAR(255) NOT NULL,
  `targetId` CHAR(36) NOT NULL,
  `purpose` VARCHAR(50) NOT NULL,
  `expiresAt` TIMESTAMP NOT NULL,
  `createdAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_tokens_hash` (`tokenHash`),
  INDEX `idx_tokens_target` (`targetId`),
  CONSTRAINT `fk_tokens_users` FOREIGN KEY (`targetId`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

