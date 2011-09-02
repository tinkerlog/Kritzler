#
# You need to have imagemagick and potrace installed
# to make this work.
#


format=500x500
edge=2

cv=/usr/local/bin/convert
cv_std="$cv  -resize $format -threshold $2% -median 1"
cv_edge="$cv -resize $format -median 2 -colorspace Gray -edge $edge -negate -threshold $3%" 

pr=/Users/alex/bin/potrace
prall="-s -t 15 -a 1.3"

$cv_std -median 1 $1.jpg $1-s.jpg
$cv_std -median 1 $1.jpg $1.pbm
$pr $prall -o $1.svg $1.pbm

$cv_edge $1.jpg $1-c.jpg
$cv -median 4 $1-c.jpg $1-cc.jpg

composite -compose Darken $1-s.jpg $1-cc.jpg $1-z.pbm

$pr $prall -o $1a.svg $1-z.pbm



