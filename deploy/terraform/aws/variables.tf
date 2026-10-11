variable "region" {
  description = "AWS region for all resources."
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Name prefix and Project tag for all resources."
  type        = string
  default     = "research-agent"
}

variable "instance_type" {
  description = "EC2 instance type of the single k3s node."
  type        = string
  default     = "t3.xlarge"
}

variable "root_volume_size" {
  description = "Size of the encrypted gp3 root volume in GiB."
  type        = number
  default     = 60

  validation {
    condition     = var.root_volume_size >= 30
    error_message = "root_volume_size must be at least 30 GiB."
  }
}

variable "admin_cidr" {
  description = "CIDR allowed to reach SSH (22) and the Kubernetes API (6443), for example 203.0.113.10/32."
  type        = string

  validation {
    condition     = can(cidrhost(var.admin_cidr, 0))
    error_message = "admin_cidr must be a valid IPv4 CIDR block."
  }
}

variable "ssh_public_key" {
  description = "Public SSH key installed for the ubuntu user."
  type        = string
}

variable "k3s_version" {
  description = "k3s release to install."
  type        = string
  default     = "v1.36.4+k3s1"
}

variable "k8s_namespace" {
  description = "Namespace that receives the ECR image pull secret."
  type        = string
  default     = "research"
}

variable "vpc_cidr" {
  description = "CIDR block of the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block of the public subnet."
  type        = string
  default     = "10.0.1.0/24"
}
