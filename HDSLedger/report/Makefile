all: report-1.pdf report-2.pdf
	
report-%.pdf: report-%.tex
	pdflatex -enable-write18 $<
	rm -f *.log *.aux *.out

clean:
	rm -f *.log *.aux *.out
