{{- define "research-agent.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "research-agent.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/name: {{ include "research-agent.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "research-agent.selectorLabels" -}}
app.kubernetes.io/name: {{ include "research-agent.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "research-agent.componentLabels" -}}
{{ include "research-agent.labels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "research-agent.image" -}}
{{- $tag := .image.tag | default .root.Chart.AppVersion -}}
{{- if .root.Values.image.registry -}}
{{- printf "%s/%s:%s" .root.Values.image.registry .image.repository $tag -}}
{{- else -}}
{{- printf "%s:%s" .image.repository $tag -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "research-agent.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.postgres.name" -}}
{{- printf "%s-postgres" (include "research-agent.fullname" .) -}}
{{- end -}}

{{- define "research-agent.kafka.name" -}}
{{- printf "%s-kafka" (include "research-agent.fullname" .) -}}
{{- end -}}

{{- define "research-agent.milvus.name" -}}
{{- printf "%s-milvus" (include "research-agent.fullname" .) -}}
{{- end -}}

{{- define "research-agent.postgres.secretName" -}}
{{- if .Values.infra.enabled -}}
{{- default (include "research-agent.postgres.name" .) .Values.infra.postgres.existingSecret -}}
{{- else -}}
{{- required "external.postgres.existingSecret is required when infra.enabled=false" .Values.external.postgres.existingSecret -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.postgres.secretKey" -}}
{{- if .Values.infra.enabled -}}
password
{{- else -}}
{{- .Values.external.postgres.passwordKey -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.datasourceUrl" -}}
{{- if .Values.infra.enabled -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (include "research-agent.postgres.name" .) .Values.infra.postgres.database -}}
{{- else -}}
{{- required "external.postgres.url is required when infra.enabled=false" .Values.external.postgres.url -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.datasourceUsername" -}}
{{- if .Values.infra.enabled -}}
{{- .Values.infra.postgres.username -}}
{{- else -}}
{{- .Values.external.postgres.username -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.kafkaBootstrap" -}}
{{- if .Values.infra.enabled -}}
{{- printf "%s:9092" (include "research-agent.kafka.name" .) -}}
{{- else -}}
{{- required "external.kafka.bootstrapServers is required when infra.enabled=false" .Values.external.kafka.bootstrapServers -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.milvusUri" -}}
{{- if .Values.infra.enabled -}}
{{- printf "http://%s:19530" (include "research-agent.milvus.name" .) -}}
{{- else -}}
{{- required "external.milvus.uri is required when infra.enabled=false" .Values.external.milvus.uri -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.springProfile" -}}
{{- .Values.researchService.springProfile | default (ternary "dev" "prod" .Values.infra.enabled) -}}
{{- end -}}

{{- define "research-agent.apiKeys.secretName" -}}
{{- default (printf "%s-api-keys" (include "research-agent.fullname" .)) .Values.researchService.security.existingSecret -}}
{{- end -}}

{{- define "research-agent.secretValue" -}}
{{- if .value -}}
{{- .value | b64enc | quote -}}
{{- else if and .existing .existing.data (hasKey .existing.data .key) -}}
{{- index .existing.data .key | quote -}}
{{- else -}}
{{- randAlphaNum 32 | b64enc | quote -}}
{{- end -}}
{{- end -}}

{{- define "research-agent.anthropic.secretName" -}}
{{- default (printf "%s-anthropic" (include "research-agent.fullname" .)) .Values.anthropic.existingSecret -}}
{{- end -}}

{{- define "research-agent.langfuse.secretName" -}}
{{- default (printf "%s-langfuse" (include "research-agent.fullname" .)) .Values.langfuse.existingSecret -}}
{{- end -}}
