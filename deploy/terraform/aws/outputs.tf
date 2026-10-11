output "public_ip" {
  description = "Elastic IP of the k3s node."
  value       = aws_eip.node.public_ip
}

output "ssh_command" {
  description = "SSH into the k3s node."
  value       = "ssh ubuntu@${aws_eip.node.public_ip}"
}

output "ecr_repository_urls" {
  description = "ECR repository URLs by image name."
  value       = { for name, repository in aws_ecr_repository.images : name => repository.repository_url }
}

output "ecr_registry" {
  description = "ECR registry host, usable as the Helm value image.registry."
  value       = local.ecr_registry
}

output "image_pull_secret" {
  description = "Image pull secret refreshed on the node in the target namespace."
  value       = "ecr-credentials"
}

output "kubeconfig_command" {
  description = "Fetch a kubeconfig that points at the node's public IP."
  value       = "ssh ubuntu@${aws_eip.node.public_ip} sudo cat /etc/rancher/k3s/k3s.yaml | sed 's/127.0.0.1/${aws_eip.node.public_ip}/' > kubeconfig-${var.project}.yaml"
}
