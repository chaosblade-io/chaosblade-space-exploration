{{/*
Expand the name of the chart.
*/}}
{{- define "chaosblade-space-exploration.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "chaosblade-space-exploration.fullname" -}}
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
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "chaosblade-space-exploration.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "chaosblade-space-exploration.labels" -}}
helm.sh/chart: {{ include "chaosblade-space-exploration.chart" . }}
{{ include "chaosblade-space-exploration.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "chaosblade-space-exploration.selectorLabels" -}}
app.kubernetes.io/name: {{ include "chaosblade-space-exploration.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "chaosblade-space-exploration.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "chaosblade-space-exploration.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the name of the database secret
*/}}
{{- define "chaosblade-space-exploration.dbSecretName" -}}
{{- if .Values.database.existingSecret }}
{{- .Values.database.existingSecret }}
{{- else }}
{{- include "chaosblade-space-exploration.fullname" . }}-db-secret
{{- end }}
{{- end }}

{{/*
Create the name of the redis secret
*/}}
{{- define "chaosblade-space-exploration.redisSecretName" -}}
{{- if .Values.redis.existingSecret }}
{{- .Values.redis.existingSecret }}
{{- else }}
{{- include "chaosblade-space-exploration.fullname" . }}-redis-secret
{{- end }}
{{- end }}

{{/*
Create the name of the kubernetes secret
*/}}
{{- define "chaosblade-space-exploration.k8sSecretName" -}}
{{- if .Values.kubernetes.existingSecret }}
{{- .Values.kubernetes.existingSecret }}
{{- else }}
{{- include "chaosblade-space-exploration.fullname" . }}-k8s-secret
{{- end }}
{{- end }}

{{/*
Create the name of the llm secret
*/}}
{{- define "chaosblade-space-exploration.llmSecretName" -}}
{{- if .Values.llm.existingSecret }}
{{- .Values.llm.existingSecret }}
{{- else }}
{{- include "chaosblade-space-exploration.fullname" . }}-llm-secret
{{- end }}
{{- end }}
