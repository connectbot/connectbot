#!/usr/bin/perl
#
# ConnectBot: simple, powerful, open-source SSH client for Android
# Copyright 2011 Kenny Root, Jeffrey Sharkey
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

use strict;
use warnings;

use Getopt::Long;
use Pod::Usage;
use LWP::UserAgent;
#use LWP::Debug qw(+);
use HTTP::Cookies;
use HTML::Form;
use Data::Dumper;
use IO::File;
use XML::LibXML;
use Locale::Language;
use Locale::Country;

my %config = (
	'url' => undef,
	'username' => undef,
	'password' => undef,
	'build' => undef,
	'branch' => undef,
);

my $baseUrl;
my $fileRegex;

sub setupUA() {
	my $ua = LWP::UserAgent->new;
	push @{ $ua->requests_redirectable }, 'POST';
	$ua->cookie_jar(HTTP::Cookies->new);

	return $ua;
}

sub getLoginToken($) {
	my $ua = shift;

	my $response = $ua->get("https://www.google.com/accounts/Login");
	my @forms = HTML::Form->parse($response->content, $response->base);

	my $f = shift @forms;

	$f->param('Email', $config{'username'});
	$f->param('Passwd', $config{'password'});

	my $req = $f->click();
	$response = $ua->request($req);

	if (not $response->is_success) {
		print "Got the error " . $response->status_line . "\n";
		die "cannot login";
	}
}

sub uploadFile($$) {
	my $ua = shift;
	my $targetFile = shift;

	# Go to package upload
	my $response = $ua->get($baseUrl . "entry");
	my @forms = HTML::Form->parse($response->content, $response->base);
	my $f = $forms[$#forms];

	my $summary = $config{'appname'} . " " . $config{'branch'} . " development snapshot";
	if (defined($config{'build'})) {
		$summary .= " " . $config{'build'};
	}

	$f->param('summary', $summary);

	$f->param('file', $targetFile);
	$f->param('label', 'Type-Installer', 'Featured');

	my $req = $f->click('btn');

	$response = $ua->request($req);
	return ($response->code eq "302");
}

sub unfeature($$) {
	my $ua = shift;
	my $url = shift;

	print "unfeaturing $url\n";
	my $response = $ua->get($url);

	if (not $response->is_success) {
		warn "couldn't reach $url";
		return 0;
	}

	my @forms = HTML::Form->parse($response->content, $response->base);
	my $f = $forms[2];

	my $i = 0;
	my $input = $f->find_input('label', 'text', $i++);
	while ($i != -1 and defined($input)) {
		if ($input->value eq 'Featured') {
			$input->value('');
			$i = -1;
		} else {
			$input = $f->find_input('label', 'text', $i++);
		}
	}

	my $req = $f->click();
	$response = $ua->request($req);

	if (not $response->is_success) {
		warn "unfeature: got the error " . $response->status_line . "\n";
		return 0;
	}

	return 1;
}

sub deleteFile($$) {
	my $ua = shift;
	my $name = shift;

	my $url = $baseUrl . "delete?name=" . $name;
	my $response = $ua->get($url);

	if (not $response->is_success) {
		warn "deleteFile: couldn't reach $url";
		return 0;
	}

	my @forms = HTML::Form->parse($response->content, $response->base);
	my $f = $forms[2];

	my $req = $f->click('delete');
	$response = $ua->request($req);

	if (not $response->is_success) {
		warn "deleteFile: got the error " . $response->status_line . "\n";
		return 0;
	}

	print "Deleted $name\n";

	return 1;
}

sub fixFeatured($) {
	my $ua = shift;

	my $response = $ua->get($baseUrl . 'list?q=label:Featured');

	my $parser = XML::LibXML->new();
	$parser->recover(1);
	my $doc = $parser->parse_html_string($response->content);

	my $root = $doc->getDocumentElement;
	my @nodes = $root->findnodes('//table[@id="resultstable"]//td[contains(@class, "col_1") and a[@class="label" and contains(@href, "Featured")]]/a[1]/@href');

	foreach my $node (@nodes) {
		my $href = $node->findvalue('.');
		next if ($href !~ $fileRegex);

		unfeature($ua, $baseUrl . $href) if ($2 ne $config{'build'} and $1 eq $config{'branch'});
	}
}

sub deleteUndownloaded($$) {
	my $ua = shift;
	my $threshhold = shift;

	my $offset = 0;

	my $parser = XML::LibXML->new();
	$parser->recover(1);

	my @toDelete = ();

	while (1) {
		my $response = $ua->get($baseUrl . 'list?start=' . $offset);

		my $doc = $parser->parse_html_string($response->content);

		my $root = $doc->getDocumentElement;
		my @nodes = $root->findnodes('//div[contains(., "Your search did not generate any results.")]');

		last if $#nodes > -1;

		@nodes = $root->findnodes('//table[@id="resultstable"]//tr[@id="headingrow"]/th[starts-with(normalize-space(a), "DownloadCount")]/@class');
		die 'Could not find DownloadCount header column' if ($#nodes == -1);

		my $downloadCountClass = $nodes[0]->findvalue('.');

		@nodes = $root->findnodes('//table[@id="resultstable"]//td[contains(@class, "col_1") and not(a[@class="label" and contains(@href, "Featured")]) and ../td[contains(@class, "'.$downloadCountClass.'") and normalize-space(.) <= "'.$threshhold.'"]]/a[1]/@href');

		foreach my $node (@nodes) {
			my $href = $node->findvalue('.');
			next if ($href !~ $fileRegex);

			if ($href =~ /detail\?name=([^&]+)&/) {
				push @toDelete, $1;
				print "Pushing on $1\n";
			}
		}

		$offset += 100;
	}

	#foreach my $href (@toDelete) {
	#	deleteFile($ua, $href);
	#}
}

pod2usage(1) if ($#ARGV < 0);


GetOptions(\%config,
	'appname=s',
	'username=s',
	'password=s',
	'build=s',
	'branch=s',
);

my $projectName = lc($config{'appname'});
$baseUrl = 'http://code.google.com/p/' . $projectName . '/downloads/';
$fileRegex = qr/$config{'appname'}-git-([a-z]*)-([0-9_-]*)(-[a-zA-Z]*)?\.apk/;

my $ua = setupUA();
getLoginToken($ua);

my $file = shift @ARGV;

uploadFile($ua, $file);

fixFeatured($ua);

deleteUndownloaded($ua, 10);

__END__
=head1 NAME

google-code-upload.pl - Upload builds to Google Code

=head1 SYNOPSIS

google-code-upload.pl [options] <file>

=head1 OPTIONS

=over 8

=item B<--username>

Username which has permission to uplaod files.

=item B<--password>

Password for user matching the username.

=item B<--appname>

Name used for the project's name (lowercase) and for matching existing
files in download.

=item B<--build>

Build identifier (e.g., 2011-08-01-0001)

=item B<--branch>

Branch that this build belongs to. Optional.

=back

=head1 DESCRIPTION

B<This program> uploads builds to a Google Code website, features the new
uploads, and unfeatures any old uploades.

=head1 AUTHOR

Kenny Root

=head1 COPYRIGHT and LICENSE

Copyright 2011 Kenny Root, Jeffrey Sharkey

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

=cut
