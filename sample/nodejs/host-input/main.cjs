"node";

const readline = require('node:readline/promises');

async function main() {
  const lines = readline.createInterface({ input: process.stdin, output: process.stdout });
  try {
    const name = await lines.question('Your name: ');
    console.log(`Hello, ${name}!`);
    const next = await lines.question('One more line: ');
    console.log(`Received: ${next}`);
    console.log('sample.host-input=PASS');
  } finally {
    lines.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
