{ pkgs ? import <nixpkgs> { } }:
pkgs.mkShell {
  packages = [
    pkgs.hello
    pkgs.temurin-bin-20
    pkgs.mongodb-tools
    pkgs.python310Full
    pkgs.python310Packages.pip
    pkgs.python310Packages.python-lsp-server
  ];

  shellHook = ''
    if [ -e .pythonenv ]; then
             echo "Python env already there - keeping it"
    else
        echo "Creating python venv"
         mkdir .pythonenv
         python3.10 -m venv .pythonenv
    fi
    source .pythonenv/bin/activate
    echo "Python venv activated..."
  '';
}
