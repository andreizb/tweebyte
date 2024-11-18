rm -rf results.jtl output.jtl jmeter-report 2> /dev/null

jmeter -n -t tweets-get.jmx -l results.jtl

/opt/homebrew/Cellar/jmeter/5.6.3/libexec/bin/FilterResults.sh --output-file ./output.jtl --input-file ./results.jtl --start-offset 60
