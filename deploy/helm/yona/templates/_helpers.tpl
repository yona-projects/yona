{{- define "yona.fullname" -}}
{{- if contains .Chart.Name .Release.Name -}}{{ .Release.Name | trunc 63 | trimSuffix "-" }}{{- else -}}{{ printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}{{- end -}}
{{- end -}}

{{- define "yona.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- define "yona.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "yona.image" -}}
{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}
{{- end -}}

{{/* 비밀번호가 든 Secret의 이름: 기존 Secret이 있으면 그것을, 아니면 차트가 만든 것을 쓴다. */}}
{{- define "yona.dbSecretName" -}}
{{- if .Values.database.existingSecret -}}{{ .Values.database.existingSecret }}{{- else -}}{{ include "yona.fullname" . }}-db{{- end -}}
{{- end -}}

{{/* H2가 아니면 접속 주소가 필요하다. */}}
{{- define "yona.databaseUrl" -}}
{{- if eq .Values.database.type "h2" -}}jdbc:h2:file:/data/db;DB_CLOSE_DELAY=-1
{{- else -}}{{ required "database.url is required unless database.type is h2" .Values.database.url }}{{- end -}}
{{- end -}}
