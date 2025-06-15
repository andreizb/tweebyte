rm -rf results.jtl output.jtl jmeter-report 2> /dev/null

jmeter -n -t user-summary.jmx -l results.jtl
# jmeter -n -t follow-create.jmx -l results.jtl
# jmeter -n -t tweet-update.jmx -l results.jtl

/opt/homebrew/Cellar/jmeter/5.6.3/libexec/bin/FilterResults.sh --output-file ./output.jtl --input-file ./results.jtl --start-offset 60
