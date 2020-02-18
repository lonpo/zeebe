package test

import (
	"context"
	"fmt"
	"github.com/testcontainers/testcontainers-go"
	"github.com/zeebe-io/zeebe/clients/go/internal/containersuite"
	"github.com/zeebe-io/zeebe/clients/go/pkg/zbc"
	"io/ioutil"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestTemp(t *testing.T) {
	binds := make(map[string]string)

	hostDir, err := ioutil.TempDir("", "some-dir")
	if err != nil {
		t.Fatal(err)
	}

	defer func() {
		if err = os.RemoveAll(hostDir); err != nil {
			if _, err = fmt.Fprintf(os.Stderr, "failed to remove tmp dir: %s", err.Error()); err != nil {
				panic(err)
			}
		}
	}()

	binds[hostDir] = "/usr/local/zeebe/data"

	req := testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			Image:        "camunda/zeebe:current-test",
			ExposedPorts: []string{"26500"},
			WaitingFor:   containersuite.ZeebeWaitStrategy{WaitTime: time.Second},
			BindMounts:   binds,
		},
		Started: true,
	}

	ctx := context.Background()
	container, err := testcontainers.GenericContainer(ctx, req)
	if err != nil {
		t.Fatal(err)
	}

	host, err := container.Host(ctx)
	if err != nil {
		t.Fatal(err)
	}

	port, err := container.MappedPort(ctx, "26500")
	if err != nil {
		t.Fatal(err)
	}

	gatewayAddress := fmt.Sprintf("%s:%d", host, port.Int())

	client, err := zbc.NewClient(&zbc.ClientConfig{UsePlaintextConnection: true, GatewayAddress: gatewayAddress})
	if err != nil {
		t.Fatal(err)
	}

	_, err = client.NewDeployWorkflowCommand().AddResourceFile("./testdata/service_task.bpmn").Send(context.Background())
	if err != nil {
		t.Fatal(err)
	}

	dir, err := ioutil.ReadDir(hostDir)
	if err != nil {
		t.Fatal(err)
	}

	if len(dir) == 0 {
		t.Fatalf("expected %s to contain files\n", hostDir)
	}

	err = filepath.Walk(hostDir, func(path string, file os.FileInfo, err error) error {
		if err == nil {
			fmt.Printf("%s\n", file.Name())
		}

		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}
