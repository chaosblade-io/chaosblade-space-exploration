{{/*
Expand the name of the chart.
*/}}
{{- define "space-exploration.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "space-exploration.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "space-exploration.labels" -}}
helm.sh/chart: {{ include "space-exploration.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: chaosblade-space-exploration
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{/*
Selector labels for a specific component.
Usage: {{ include "space-exploration.selectorLabels" (dict "component" "task-resource" "context" .) }}
*/}}
{{- define "space-exploration.selectorLabels" -}}
app.kubernetes.io/name: {{ .component }}
app.kubernetes.io/instance: {{ .context.Release.Name }}
{{- end }}

{{/*
MySQL host – points at the in-cluster Service when mysql.enabled=true.
*/}}
{{- define "space-exploration.mysqlHost" -}}
{{- printf "%s-mysql.%s.svc.cluster.local" (include "space-exploration.fullname" .) .Values.namespace }}
{{- end }}

{{/*
Redis host – points at the in-cluster Service when redis.enabled=true.
*/}}
{{- define "space-exploration.redisHost" -}}
{{- printf "%s-redis.%s.svc.cluster.local" (include "space-exploration.fullname" .) .Values.namespace }}
{{- end }}

{{/*
Full image reference for a service.
Usage: {{ include "space-exploration.image" (dict "image" .Values.taskResource.image "context" .) }}
*/}}
{{- define "space-exploration.image" -}}
{{- printf "%s/%s:%s" .context.Values.registry .image .context.Values.imageTag }}
{{- end }}

{{/*
ServiceAccount name used by services that need K8s API access.
*/}}
{{- define "space-exploration.serviceAccountName" -}}
{{- printf "%s-sa" (include "space-exploration.fullname" .) }}
{{- end }}
