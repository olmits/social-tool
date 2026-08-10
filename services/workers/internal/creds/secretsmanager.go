package creds

import (
	"context"
	"fmt"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
)

// SecretsManagerStore resolves references against AWS Secrets Manager, where a reference is
// the secret's name or ARN. Access comes from the task's IAM role — no credentials are
// configured here, and none are stored in the database or the environment.
type SecretsManagerStore struct {
	client *secretsmanager.Client
}

// NewSecretsManagerStore builds a store using the ambient AWS configuration (region,
// credentials chain, IAM role).
func NewSecretsManagerStore(ctx context.Context, region string) (*SecretsManagerStore, error) {
	opts := []func(*awsconfig.LoadOptions) error{}
	if region != "" {
		opts = append(opts, awsconfig.WithRegion(region))
	}

	cfg, err := awsconfig.LoadDefaultConfig(ctx, opts...)
	if err != nil {
		return nil, fmt.Errorf("load aws config: %w", err)
	}
	return &SecretsManagerStore{client: secretsmanager.NewFromConfig(cfg)}, nil
}

// Resolve fetches the secret string for ref.
func (s *SecretsManagerStore) Resolve(ctx context.Context, ref string) (string, error) {
	if ref == "" {
		return "", fmt.Errorf("credential reference is empty")
	}

	out, err := s.client.GetSecretValue(ctx, &secretsmanager.GetSecretValueInput{SecretId: &ref})
	if err != nil {
		// The AWS error carries the secret's name but never its value.
		return "", fmt.Errorf("get secret %s: %w", ref, err)
	}
	if out.SecretString == nil {
		return "", fmt.Errorf("secret %s has no string value", ref)
	}
	return *out.SecretString, nil
}
